package splunk

import com.typesafe.config.Config
import play.api.http.{HeaderNames, MimeTypes}
import play.api.libs.json.{Json, OFormat, OWrites, Reads}
import play.api.libs.ws.{WSClient, WSResponse}
import splunk.SplunkJsonCodecs._

import java.time.Instant
import scala.concurrent.Future
import scala.concurrent.duration.FiniteDuration

object SplunkJsonCodecs {
  implicit val splunkFormat: OFormat[SplunkEvent] = Json.format[SplunkEvent]
  implicit val splunkEventReads: Reads[SplunkEvent] =
    Json.reads[SplunkEvent]
  implicit val splunkEventWrites: OWrites[SplunkEvent] =
    Json.writes[SplunkEvent]
}

/*
{
    "time": 1426279439,
    "host": "localhost",
    "source": "random-data-generator",
    "sourcetype": "my_sample_data",
    "index": "gmax",
    "event":  "Hello world!"
}
*/

case class SplunkEvent(
    time: Long,
    host: String,
    source: String,
    sourcetype: String,
    index: String,
    event: String
)

case class Splunk(
    ws: WSClient,
    requestTimeout: FiniteDuration,
    cfg: Config
) {
  def ingestEvent(
      event: SplunkEvent
  ): Future[WSResponse] =
    ws.url(s"${cfg.getString("splunk.uri")}/collector/event")
      .withVirtualHost(cfg.getString("splunk.virtualHost"))
      .withHttpHeaders(
        HeaderNames.AUTHORIZATION -> s"Splunk ${cfg.getString("splunk.token")}",
        HeaderNames.CONTENT_TYPE -> MimeTypes.JSON
      )
      .withRequestTimeout(requestTimeout)
      .post(Json.toJson(splunkEvent(event)))

  def splunkEvent(event: SplunkEvent): SplunkEvent =
    event.copy(time = Instant.now().getEpochSecond)
}
