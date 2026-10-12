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
import play.api.libs.ws.WSRequest
import play.api.http.HeaderNames
import play.api.libs.ws.JsonBodyWritables.writeableOf_JsValue
import support.*
import uk.gov.hmrc.senioraccountingofficer.controllers.{
  NotificationControllerISpec as Notification,
  CertificateControllerISpec as Certificate
}
import uk.gov.hmrc.senioraccountingofficer.models.submission.SubmissionStep
import uk.gov.hmrc.senioraccountingofficer.services.submission.SubmissionWorker

class SubmissionV2ISpec extends ISpecBase {

  override protected def usesSubmissionQueues: Boolean = true

  override def additionalConfigs: Map[String, Any] = Map(
    "microservice.services.hip.host"                        -> wireMockHost,
    "microservice.services.hip.port"                        -> wireMockPort,
    "microservice.services.object-store.host"               -> wireMockHost,
    "microservice.services.object-store.port"               -> wireMockPort,
    "microservice.services.email.host"                      -> wireMockHost,
    "microservice.services.email.port"                      -> wireMockPort,
    "microservice.services.secure-data-exchange-proxy.host" -> wireMockHost,
    "microservice.services.secure-data-exchange-proxy.port" -> wireMockPort
  )

  private def makeRequest(path: String, correlationId: String): WSRequest = wsClient
    .url(s"$baseUrl/senior-accounting-officer/v2/$path")
    .withHttpHeaders(
      HeaderNames.AUTHORIZATION -> MockAuthHelper.testBearerToken,
      "correlationId"           -> correlationId
    )

  "POST /v2/notification and GET /v2/notification/:idempotencyKey" must {
    "return pending until the initial PDF attempt, then deliver emails and documents independently" in {
      import Notification.*

      MockAuthHelper.mockAuthOk()
      GetSubscriptionHelper.mock(MockAuthHelper.testSubscriptionId, 200, Some(getSubscriptionResponse))
      RetrieveCustomerHelper.mock(200, Some(retrieveCustomerResponseCustomerFound))
      SubmitNotificationHelper.mock(MockAuthHelper.testSubscriptionId, 201, Some(submitNotificationResponse))
      EmailHelper.mock(202)
      ObjectStoreHelper.mockPdfUpload(pdfFilename, 200, Some(objectStoreUploadResponse))
      ObjectStoreHelper.mockPdfRetrieval(pdfFilename, 200, Some(objectStoreUploadResponse))
      ObjectStoreHelper.mockNotificationZipUpload(notificationReference, 200, Some(objectStoreUploadResponse))
      SdesHelper.mock(202, None)

      val accepted = makeRequest("notification", correlationId).post(Json.parse(requestBody)).futureValue
      accepted.status mustBe 202
      val key = (accepted.json \ "idempotencyKey").as[String]
      makeRequest(s"notification/$key", correlationId).get().futureValue.status mustBe 204
      GetSubscriptionHelper.verifyCalled(MockAuthHelper.testSubscriptionId, 0)

      val worker = app.injector.instanceOf[SubmissionWorker]
      Seq(SubmissionStep.RetrieveSubscription, SubmissionStep.RetrieveCustomer, SubmissionStep.SubmitDps)
        .foreach(step => worker.processNext(step).futureValue)
      makeRequest(s"notification/$key", correlationId).get().futureValue.status mustBe 204
      worker.processNext(SubmissionStep.InitialPdf).futureValue

      val completed = makeRequest(s"notification/$key", correlationId).get().futureValue
      completed.status mustBe 200
      completed.json mustBe Json.obj("notificationRef" -> notificationReference)

      (1 to 2).foreach(_ => worker.processNext(SubmissionStep.SendEmail).futureValue)
      Seq(SubmissionStep.EnsurePdf, SubmissionStep.PackageDocumentum, SubmissionStep.NotifySdes)
        .foreach(step => worker.processNext(step).futureValue)

      EmailHelper.verifyCalled(None, 2)
      ObjectStoreHelper.verifyPdfUpload(pdfFilename, 1)
      SubmitNotificationHelper.verifyCalled(
        MockAuthHelper.testSubscriptionId,
        Some(submitNotificationRequestWithCustomerId),
        1
      )
      ObjectStoreHelper.verifyNotificationZipUpload(notificationReference, 1)
      SdesHelper.verifyCalled(sdesRequest, 1)
      makeRequest(s"notification/$key", correlationId).get().futureValue.status mustBe 200
    }
  }

  "POST /v2/certificate and GET /v2/certificate/:idempotencyKey" must {
    "return pending until the initial PDF attempt, then deliver emails and documents independently" in {
      import Certificate.*

      MockAuthHelper.mockAuthOk()
      GetSubscriptionHelper.mock(MockAuthHelper.testSubscriptionId, 200, Some(getSubscriptionResponse))
      RetrieveCustomerHelper.mock(200, Some(retrieveCustomerResponseCustomerFound))
      SubmitCertificateHelper.mock(MockAuthHelper.testSubscriptionId, 201, Some(submitCertificateResponse))
      EmailHelper.mock(202)
      ObjectStoreHelper.mockPdfUpload(pdfFilename, 200, Some(objectStoreUploadResponse))
      ObjectStoreHelper.mockPdfRetrieval(pdfFilename, 200, Some(objectStoreUploadResponse))
      ObjectStoreHelper.mockCertificateZipUpload(certificateReference, 200, Some(objectStoreUploadResponse))
      SdesHelper.mock(202, None)

      val accepted = makeRequest("certificate", correlationId).post(Json.parse(requestBody)).futureValue
      accepted.status mustBe 202
      val key = (accepted.json \ "idempotencyKey").as[String]
      makeRequest(s"certificate/$key", correlationId).get().futureValue.status mustBe 204
      GetSubscriptionHelper.verifyCalled(MockAuthHelper.testSubscriptionId, 0)

      val worker = app.injector.instanceOf[SubmissionWorker]
      Seq(SubmissionStep.RetrieveSubscription, SubmissionStep.RetrieveCustomer, SubmissionStep.SubmitDps)
        .foreach(step => worker.processNext(step).futureValue)
      makeRequest(s"certificate/$key", correlationId).get().futureValue.status mustBe 204
      worker.processNext(SubmissionStep.InitialPdf).futureValue

      val completed = makeRequest(s"certificate/$key", correlationId).get().futureValue
      completed.status mustBe 200
      completed.json mustBe Json.obj("certificateRef" -> certificateReference)

      (1 to 3).foreach(_ => worker.processNext(SubmissionStep.SendEmail).futureValue)
      Seq(SubmissionStep.EnsurePdf, SubmissionStep.PackageDocumentum, SubmissionStep.NotifySdes)
        .foreach(step => worker.processNext(step).futureValue)

      EmailHelper.verifyCalled(None, 3)
      ObjectStoreHelper.verifyPdfUpload(pdfFilename, 1)
      SubmitCertificateHelper.verifyCalled(MockAuthHelper.testSubscriptionId, Some(submitCertificateRequest), 1)
      ObjectStoreHelper.verifyCertificateZipUpload(certificateReference, 1)
      SdesHelper.verifyCalled(sdesRequest, 1)
      makeRequest(s"certificate/$key", correlationId).get().futureValue.status mustBe 200
    }
  }

}
