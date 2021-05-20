package oauth

import akka.http.scaladsl.model.StatusCode
import akka.http.scaladsl.model.StatusCodes._
import akka.util.ByteString
import com.typesafe.config._
import play.api.http.{HeaderNames, HttpEntity, MimeTypes}
import play.api.libs.json.JsValue
import play.api.libs.ws.{WSClient, WSResponse}
import play.api.mvc.{AnyContent, Request, ResponseHeader, Result}

import java.util.Base64
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future
import scala.concurrent.duration.FiniteDuration
import scala.util.matching.Regex

object KeycloakIntegration {
  case class AuthInfo(
      active: Boolean,
      typ: String,
      clientId: String,
      roles: List[String]
  )

  case class Vt(status: StatusCode, message: String)

  val tokenRegex: Regex = "^(?i)Bearer (.*)(?-i)".r

  val config: Config = ConfigFactory.load
  val cfg: Config = config.getConfig("keycloak")
  val host: String = cfg.getString("host")
  val port: Int = cfg.getInt("port")
  val realm: String = cfg.getString("realm")
  val client: String = cfg.getString("client")
  val secret: String = cfg.getString("secret")
  val basic: String =
    Base64.getEncoder.encodeToString(s"$client:$secret".getBytes)

  val tokenUri =
    s"http://$host:$port/auth/realms/$realm/protocol/openid-connect/token"
  val validUri = s"$tokenUri/introspect"

  def getToken(implicit
      ws: WSClient,
      requestTimeout: FiniteDuration
  ): Future[WSResponse] =
    ws.url(tokenUri)
      .withRequestTimeout(requestTimeout)
      .withHttpHeaders(
        HeaderNames.CONTENT_TYPE -> MimeTypes.FORM
      )
      .post(
        Map(
          "grant_type" -> Seq("client_credentials"),
          "client_id" -> Seq(client),
          "client_secret" -> Seq(secret)
        )
      )

  private def validateToken(token: String)(implicit
      ws: WSClient,
      requestTimeout: FiniteDuration
  ): Future[WSResponse] =
    ws.url(validUri)
      .withRequestTimeout(requestTimeout)
      .withHttpHeaders(
        HeaderNames.CONTENT_TYPE -> MimeTypes.FORM,
        HeaderNames.AUTHORIZATION -> s"Basic $basic"
      )
      .post(Map("token" -> token))

  private def parseToken(token: JsValue): AuthInfo =
    AuthInfo(
      active = (token \ "active").asOpt[Boolean].getOrElse(false),
      typ = (token \ "typ").asOpt[String].getOrElse("?"),
      clientId = (token \ "client_id").asOpt[String].getOrElse("?"),
      roles = (token \ "realm_access" \ "roles")
        .asOpt[List[String]]
        .getOrElse(Nil)
        .map(_.toUpperCase)
    )

  private def tokenValidation(authorization: String): Either[String, String] =
    authorization match {
      case tokenRegex(jwtToken) => Right(jwtToken.trim)
      case _                    => Left("This is not a `Bearer` token or has an invalid format")
    }

  private def authorize(
      authorization: String,
      roles: Seq[String]
  )(implicit ws: WSClient, requestTimeout: FiniteDuration): Future[Vt] =
    tokenValidation(authorization.trim).fold(
      error => Future.successful(Vt(Unauthorized, error)),
      token =>
        for {
          response <- validateToken(token)
        } yield validateResponse(response, roles)
    )

  private def validateResponse(response: WSResponse, roles: Seq[String]): Vt =
    response.status match {
      case OK.intValue =>
        val authInfo = parseToken(response.json)
        if (!authInfo.active)
          Vt(Unauthorized, "Your token is expired")
        else if (authInfo.clientId != client)
          Vt(Unauthorized, "Your token contains an invalid client ID")
        else if (authInfo.typ.toLowerCase != "bearer")
          Vt(Unauthorized, "Only bearer tokens are accepted")
        else if (authInfo.roles.contains("ROLE_ADMIN"))
          Vt(OK, "By default, ROLE_ADMIN has all rights")
        else if (!roles.exists(authInfo.roles.contains(_)))
          Vt(Forbidden, "You have no permissions to do this operation")
        else
          Vt(OK, "Roles are OK")
      case _ =>
        Vt(Unauthorized, "Your token has been invalidated by the server")
    }

  def withAuthorization(roles: String*)(
      block: => Future[Result]
  )(implicit
      request: Request[AnyContent],
      ws: WSClient,
      requestTimeout: FiniteDuration
  ): Future[Result] = {

    def toResult(code: StatusCode, body: String): Future[Result] =
      Future.successful(
        Result(
          ResponseHeader(code.intValue()),
          HttpEntity.Strict(ByteString.fromString(body), None)
        )
      )

    request.headers
      .get(HeaderNames.AUTHORIZATION)
      .fold(
        toResult(Unauthorized, "No authorization token has been found!")
      )(token =>
        (for {
          result <- authorize(token, roles)
        } yield
          if (result.status == OK) block
          else toResult(result.status, result.message)).flatten
      )
  }
}
