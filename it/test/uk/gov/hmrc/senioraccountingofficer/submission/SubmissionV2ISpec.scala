/*
 * Copyright 2026 HM Revenue & Customs
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package uk.gov.hmrc.senioraccountingofficer.submission

import play.api.libs.json.Json
import play.api.libs.ws.JsonBodyWritables.writeableOf_JsValue
import support.*
import uk.gov.hmrc.senioraccountingofficer.controllers.{NotificationControllerISpec as N, CertificateControllerISpec as C}
import uk.gov.hmrc.senioraccountingofficer.models.submission.{SubmissionKind, SubmissionStep}
import uk.gov.hmrc.senioraccountingofficer.services.submission.SubmissionWorker

class SubmissionV2ISpec extends ISpecBase {
  override protected def usesSubmissionQueues: Boolean = true

  override def additionalConfigs: Map[String, Any] = Seq("hip", "email", "object-store", "secure-data-exchange-proxy")
    .flatMap(service => Seq(s"microservice.services.$service.host" -> wireMockHost, s"microservice.services.$service.port" -> wireMockPort)).toMap

  for kind <- SubmissionKind.values do {
    s"$kind v2 orchestration" should {
      "return pending, then success, and deliver documents and emails without changing status" in {
        val isNotification = kind == SubmissionKind.Notification
        val body = if isNotification then N.requestBody else C.requestBody
        val pdfFile = if isNotification then N.pdfFilename else C.pdfFilename
        val reference = if isNotification then N.notificationReference else C.certificateReference
        val subscription = if isNotification then N.getSubscriptionResponse else C.getSubscriptionResponse
        val customer = if isNotification then N.retrieveCustomerResponseCustomerFound else C.retrieveCustomerResponseCustomerFound
        val objectResponse = if isNotification then N.objectStoreUploadResponse else C.objectStoreUploadResponse
        MockAuthHelper.mockAuthOk()
        GetSubscriptionHelper.mock(MockAuthHelper.testSubscriptionId, 200, Some(subscription))
        RetrieveCustomerHelper.mock(200, Some(customer))
        if isNotification then SubmitNotificationHelper.mock(MockAuthHelper.testSubscriptionId, 201, Some(N.submitNotificationResponse))
        else SubmitCertificateHelper.mock(MockAuthHelper.testSubscriptionId, 201, Some(C.submitCertificateResponse))
        EmailHelper.mock(202)
        ObjectStoreHelper.mockPdfUpload(pdfFile, 200, Some(objectResponse))
        ObjectStoreHelper.mockPdfRetrieval(pdfFile, 200, Some(objectResponse))
        if isNotification then ObjectStoreHelper.mockNotificationZipUpload(reference, 200, Some(objectResponse))
        else ObjectStoreHelper.mockCertificateZipUpload(reference, 200, Some(objectResponse))
        SdesHelper.mock(200, None)

        val url = s"$baseUrl/senior-accounting-officer/v2/${kind.toString.toLowerCase}"
        def request(target: String) = wsClient.url(target).withHttpHeaders("Authorization" -> MockAuthHelper.testBearerToken, "correlationId" -> (if isNotification then N.correlationId else C.correlationId))
        val accepted = request(url).post(Json.parse(body)).futureValue
        accepted.status mustBe 202
        val key = (accepted.json \ "idempotencyKey").as[String]
        request(s"$url/$key").get().futureValue.status mustBe 204
        GetSubscriptionHelper.verifyCalled(MockAuthHelper.testSubscriptionId, 0)

        val worker = app.injector.instanceOf[SubmissionWorker]
        Seq(SubmissionStep.RetrieveSubscription, SubmissionStep.RetrieveCustomer, SubmissionStep.SubmitDps).foreach(worker.processNext(_).futureValue)
        request(s"$url/$key").get().futureValue.status mustBe 204
        worker.processNext(SubmissionStep.InitialPdf).futureValue
        val completed = request(s"$url/$key").get().futureValue
        completed.status mustBe 200
        completed.json mustBe Json.obj((if isNotification then "notificationRef" else "certificateRef") -> reference)

        val recipients = if isNotification then 2 else 3
        (1 to recipients).foreach(_ => worker.processNext(SubmissionStep.SendEmail).futureValue)
        Seq(SubmissionStep.EnsurePdf, SubmissionStep.PackageDocumentum, SubmissionStep.NotifySdes).foreach(worker.processNext(_).futureValue)
        EmailHelper.verifyCalled(None, recipients)
        ObjectStoreHelper.verifyPdfUpload(pdfFile, 1)
        if isNotification then {
          SubmitNotificationHelper.verifyCalled(MockAuthHelper.testSubscriptionId, Some(N.submitNotificationRequestWithCustomerId), 1)
          ObjectStoreHelper.verifyNotificationZipUpload(reference, 1)
          SdesHelper.verifyCalled(N.sdesRequest, 1)
        } else {
          SubmitCertificateHelper.verifyCalled(MockAuthHelper.testSubscriptionId, Some(C.submitCertificateRequest), 1)
          ObjectStoreHelper.verifyCertificateZipUpload(reference, 1)
          SdesHelper.verifyCalled(C.sdesRequest, 1)
        }
        request(s"$url/$key").get().futureValue.status mustBe 200
      }
    }
  }
}
