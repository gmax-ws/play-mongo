import com.typesafe.config.Config
import controllers.{OAuthController, PersonController, SplunkController}
import play.api.ApplicationLoader.Context
import play.api.BuiltInComponentsFromContext
import play.api.http.DefaultHttpErrorHandler
import play.api.libs.ws.ahc.{AhcWSClient, AhcWSClientConfigFactory}
import play.api.mvc.{EssentialFilter, RequestHeader, Result, Results}
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

  lazy val router: Router = Router.from {
    case GET(p"/api/persons")              => personController.getPersons
    case GET(p"/api/person/${int(id)}")    => personController.findPerson(id)
    case POST(p"/api/person")              => personController.createPerson
    case PUT(p"/api/person")               => personController.updatePerson()
    case DELETE(p"/api/person/${int(id)}") => personController.deletePerson(id)
    case GET(p"/token")                    => oauthController.getToken
    case POST(p"/facebook")                => oauthController.facebook
    case POST(p"/google")                  => oauthController.google
    case POST(p"/splunk")                  => splunkController.ingestEvent
  }

  override lazy val httpErrorHandler: DefaultHttpErrorHandler =
    new DefaultHttpErrorHandler(
      environment,
      configuration,
      devContext.map(_.sourceMapper),
      Some(router)
    ) {

      override protected def onNotFound(
          request: RequestHeader,
          message: String
      ): Future[Result] =
        Future.successful(Results.NotFound("Resource has not found!"))

      override protected def onBadRequest(
          request: RequestHeader,
          message: String
      ): Future[Result] =
        Future.successful(Results.BadRequest("Bad request!"))
    }

  override def httpFilters: Seq[EssentialFilter] = {
    val defaultFilters = super.httpFilters

    val disabledFilters: Set[EssentialFilter] = Set(csrfFilter)

    // Do not enable Gzip, introduces a vulnerability to BREACH attacks (CVE-2013-3587)
    val enabledFilters: Seq[EssentialFilter] = Seq(corsFilter)

    enabledFilters ++ defaultFilters.filterNot(disabledFilters.contains)
  }
}
