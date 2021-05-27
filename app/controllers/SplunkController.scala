package controllers

import play.api.libs.json.{JsValue, Json, Reads}
import play.api.libs.ws.{WSClient, WSResponse}
import play.api.mvc.{AbstractController, Action, ControllerComponents}
import splunk.{Splunk, SplunkEvent}

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.duration.FiniteDuration
import splunk.SplunkJsonCodecs._

class SplunkController(
    cc: ControllerComponents,
    splunk: Splunk
)(implicit ws: WSClient, requestTimeout: FiniteDuration)
    extends AbstractController(cc) {

  def ingestEvent: Action[JsValue] =
    Action(parse.json).async { request =>
      val event = Json.fromJson[SplunkEvent](request.body)
      splunk.ingestEvent(event.get) map { wsr: WSResponse =>
        Ok(Json.toJson(s"${wsr.status}"))
      }
    }
}
