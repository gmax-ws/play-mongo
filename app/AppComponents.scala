import akka.util.ByteString
import com.typesafe.config.Config
import controllers.PersonJsonCodecs.timestamp
import controllers.{OAuthController, PersonController, SplunkController}
import play.api.ApplicationLoader.Context
import play.api.BuiltInComponentsFromContext
import play.api.http.Status._
import play.api.http.{DefaultHttpErrorHandler, HttpEntity}
import play.api.libs.json.{JsBoolean, JsNumber, JsObject, JsString}
import play.api.libs.ws.ahc.{AhcWSClient, AhcWSClientConfigFactory}
import play.api.mvc.Results.{
  BadRequest,
  Forbidden,
  InternalServerError,
  NotFound
}
import play.api.mvc.{EssentialFilter, RequestHeader, ResponseHeader, Result}
import play.api.routing.Router
import play.api.routing.sird._
import play.filters.HttpFiltersComponents
import play.filters.cors.CORSComponents
import repo.person.{PersonMongo, PersonRepo}
import splunk.Splunk

import scala.concurrent.Future
import scala.concurrent.duration.{DurationInt, FiniteDuration}

class AppComponents(context: Context)
    extends BuiltInComponentsFromContext(context)
    with CORSComponents
    with HttpFiltersComponents
    with controllers.AssetsComponents {
  implicit lazy val requestTimeout: FiniteDuration = 10000.millis
  implicit lazy val ws: AhcWSClient = AhcWSClient(
    AhcWSClientConfigFactory.forConfig()
  )
  lazy val config: Config = configuration.underlying
  lazy val personRepo: PersonRepo = PersonMongo.repo(config)

  lazy val splunk: Splunk = Splunk(ws, requestTimeout, config)

  lazy val oauthController: OAuthController = new OAuthController(
    controllerComponents
  )

  lazy val personController: PersonController = new PersonController(
    controllerComponents,
    personRepo
  )

  lazy val splunkController: SplunkController = new SplunkController(
    controllerComponents,
    splunk
  )

  lazy val assetsPath: String = config.getString("play.assets.path")

  lazy val router: Router = Router.from {
    case GET(p"/api/persons")              => personController.getPersons
    case GET(p"/api/person/${int(id)}")    => personController.findPerson(id)
    case POST(p"/api/person")              => personController.createPerson
    case PUT(p"/api/person")               => personController.updatePerson()
    case DELETE(p"/api/person/${int(id)}") => personController.deletePerson(id)
    case GET(p"/token")                    => oauthController.getToken
    case POST(p"/login")                   => oauthController.login
    case POST(p"/facebook")                => oauthController.facebook
    case POST(p"/google")                  => oauthController.google
    case POST(p"/splunk")                  => splunkController.ingestEvent
    case GET(p"/assets")                   => assets.at(assetsPath, "index.html")
    case GET(p"/assets/$file*")            => assets.at(assetsPath, file)
  }

  override lazy val httpErrorHandler: DefaultHttpErrorHandler =
    new DefaultHttpErrorHandler(
      environment,
      configuration,
      devContext.map(_.sourceMapper),
      Some(router)
    ) {

      private def rsp(
          request: RequestHeader,
          status: Int,
          message: String
      ): JsObject =
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

      override protected def onNotFound(
          request: RequestHeader,
          message: String
      ): Future[Result] =
        Future.successful(NotFound(rsp(request, NOT_FOUND, message)))

      override protected def onBadRequest(
          request: RequestHeader,
          message: String
      ): Future[Result] =
        Future.successful(BadRequest(rsp(request, BAD_REQUEST, message)))

      override protected def onForbidden(
          request: RequestHeader,
          message: String
      ): Future[Result] =
        Future.successful(Forbidden(rsp(request, FORBIDDEN, message)))

      override protected def onOtherClientError(
          request: RequestHeader,
          statusCode: Int,
          message: String
      ): Future[Result] =
        Future.successful(
          Result(
            ResponseHeader(statusCode),
            HttpEntity.Strict(
              ByteString.fromString(
                rsp(request, statusCode, message).toString()
              ),
              None
            )
          )
        )

      override def onClientError(
          request: RequestHeader,
          statusCode: Int,
          message: String
      ): Future[Result] =
        super.onClientError(request, statusCode, message)

      override def onServerError(
          request: RequestHeader,
          exception: Throwable
      ): Future[Result] = {
        Future.successful(
          InternalServerError(
            rsp(
              request,
              INTERNAL_SERVER_ERROR,
              s"A server error occurred: ${exception.getMessage}"
            )
          )
        )
      }
    }

  override def httpFilters: Seq[EssentialFilter] = {
    val defaultFilters = super.httpFilters

    val disabledFilters: Set[EssentialFilter] = Set(csrfFilter)

    // Do not enable Gzip, introduces a vulnerability to BREACH attacks (CVE-2013-3587)
    val enabledFilters: Seq[EssentialFilter] = Seq(corsFilter)

    enabledFilters ++ defaultFilters.filterNot(disabledFilters.contains)
  }
}
