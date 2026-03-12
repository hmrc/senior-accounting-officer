package uk.gov.hmrc.senioraccountingofficer.services

import org.mockito.ArgumentMatchers.any
import org.mockito.Mockito.when
import org.scalatest.concurrent.ScalaFutures
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec
import org.scalatestplus.mockito.MockitoSugar
import uk.gov.hmrc.http.{HeaderCarrier, HttpResponse}
import uk.gov.hmrc.senioraccountingofficer.connectors.NotificationConnector
import uk.gov.hmrc.senioraccountingofficer.models.{Company, NotificationRequest, SeniorAccountingOfficer}

import java.time.LocalDate
import scala.concurrent.{ExecutionContext, Future}

class NotificationServiceSpec extends AnyWordSpec with Matchers with MockitoSugar with ScalaFutures {

  implicit val ec: ExecutionContext = ExecutionContext.global
  implicit val hc: HeaderCarrier = HeaderCarrier()

  val mockConnector: NotificationConnector = mock[NotificationConnector]
  val service = new NotificationService(mockConnector)

  "postNotification" should {

    "delegate to the connector and return the response" in {
      val request = NotificationRequest(
        companies = Seq(
          Company(
            companyName = "Example Ltd",
            uniqueTaxReference = "1234567890",
            companyReferenceNumber = "AB123456",
            companyType = "LTD",
            financialYearEndDate = LocalDate.parse("2024-12-31"),
            seniorAccountingOfficers = Seq(
              SeniorAccountingOfficer(
                name = "Firstname Lastname",
                email = "Firstname.Lastname@example.com",
                startDate = LocalDate.parse("2024-04-01"),
                endDate = LocalDate.parse("2025-03-31")
              )
            )
          )
        ),
        additionalInformation = Some("non-empty string")
      )

      val mockResponse = HttpResponse(200, "Success")
      when(mockConnector.postNotification(any(), any())(any())).thenReturn(Future.successful(mockResponse))

      val result = service.postNotification("123", request).futureValue

      result shouldBe mockResponse
    }
  }
}