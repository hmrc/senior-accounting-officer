package uk.gov.hmrc.senioraccountingofficer.controllers

import scala.concurrent.ExecutionContext
import uk.gov.hmrc.http.client.HttpClientV2
import uk.gov.hmrc.senioraccountingofficer.config.AppConfig
import javax.inject.Inject
import java.net.URL
import uk.gov.hmrc.http.HttpResponse
import uk.gov.hmrc.http.HeaderCarrier
import scala.concurrent.Future

// TODO: change name
class StubConnector @Inject() (appConfig: AppConfig, httpClient: HttpClientV2)(using ExecutionContext) {
  def getObligation(saoSubscriptionId: String)(using HeaderCarrier): Future[HttpResponse] =
    httpClient
      .get(URL(appConfig.stubsUrl + "/obligation/123"))
      .setHeader(("Authorization", "Basic " + appConfig.stubsAuth))
      .execute[HttpResponse]
}
