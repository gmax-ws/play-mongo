package controllers

import controllers.PersonJsonCodecs.timestamp
import play.api.libs.json._
import play.api.libs.ws.{WSClient, WSResponse}
import play.api.mvc.{
  AbstractController,
  Action,
  ControllerComponents,
  RequestHeader
}
import splunk.{Splunk, SplunkEvent}

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future
import scala.concurrent.duration.FiniteDuration

class SplunkController(
    cc: ControllerComponents,
    splunk: Splunk
)(implicit ws: WSClient, requestTimeout: FiniteDuration)
    extends AbstractController(cc) {

  implicit val eventWrites: OWrites[SplunkEvent] = Json.writes[SplunkEvent]
  implicit val eventFormat: OFormat[SplunkEvent] = Json.format[SplunkEvent]

  private def rsp(
      status: Int,
      message: String,
      event: SplunkEvent
  )(implicit request: RequestHeader): JsObject =
    rsp(status, message) + ("event", Json.toJson(event))

  private def rsp(
      status: Int,
      message: String
  )(implicit request: RequestHeader): JsObject =
    JsObject(
      Seq(
        "timestamp" -> JsString(timestamp),
        "version" -> JsString(request.version),
        "host" -> JsString(request.host),
        "uri" -> JsString(request.uri),
        "method" -> JsString(request.method),
        "contentType" -> JsString(request.contentType.getOrElse("")),
        "hasBody" -> JsBoolean(request.hasBody),
        "status" -> JsNumber(status),
        "message" -> JsString(message)
      )
    )

  def ingestEvent: Action[JsValue] =
    Action(parse.json).async { implicit request =>
      Json.fromJson[SplunkEvent](request.body).asOpt match {
        case Some(event) =>
          splunk.ingestEvent(event) map { wsr: WSResponse =>
            Ok(rsp(wsr.status, wsr.statusText, event))
          }
        case None =>
          Future.successful(
            BadRequest(rsp(BAD_REQUEST, "Invalid event format!"))
          )
      }
    }
}
