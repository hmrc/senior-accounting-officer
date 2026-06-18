package uk.gov.hmrc.senioraccountingofficer.models

import play.api.libs.json.{Format, Json}

final case class NotificationStubRequest (

                                           companies: List[Company],
                                           saos: List[Sao],
                                           remarks: Option[String] = None,
                                           staffPID: Option[String] = None
                                         )

private final case class Company(
                          crn: Option[String] = None,
                          utr: String,
                          name: String,
                          accPeriodEnd: String,
                          status: String,
                          `type`: String
                        )

final case class Sao(
                      name: String,
                      fromDate: String,
                      email: Option[String] = None,
                      toDate: String
                    )

//extension (request: NotificationRequest) {
//  
//}
//
object NotificationStubRequest {
  given Format[NotificationStubRequest] = Json.format[NotificationStubRequest]
}

//object Company {
//  given Format[Company] = Json.format[Company]
//}
//
//object Sao {
//  given Format[Sao] = Json.format[Sao]
//}