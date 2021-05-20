package oauth

import play.api.http.{HeaderNames, Status}
import play.api.libs.ws.{WSClient, WSResponse}

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future
import scala.concurrent.duration.FiniteDuration

object OAuth2Google {

  /** Request URI */
  private val requestUri = "https://www.googleapis.com"

  /** API version */
  private val apiVersion = "v3"

  /**
    * Google OAuth2 implementation
    *
    * @param accessToken Google access token.
    * @param idToken     Google id token.
    * @return User details
    */
  def apply(
      accessToken: String,
      idToken: String
  )(implicit
      ws: WSClient,
      requestTimeout: FiniteDuration
  ): Future[Either[Throwable, Map[String, Option[String]]]] =
    (for {
      valid <- validate(ws, idToken)
      userinfo <- userInfo(accessToken) if valid.status == Status.OK
      date <- dayOfBirth(accessToken, (userinfo.json \ "sub").as[String])
      if userinfo.status == Status.OK
    } yield {
      val birthday =
        if (date.status == Status.OK) (date.json \ "birthday").asOpt[String]
        else None
      val json = userinfo.json
      Right(
        Map(
          "user" -> (json \ "sub").asOpt[String],
          "name" -> (json \ "name").asOpt[String],
          "email" -> (json \ "email").asOpt[String],
          "gender" -> (json \ "gender").asOpt[String],
          "picture" -> (json \ "picture").asOpt[String],
          "dob" -> birthday
        )
      )
    }) recover {
      case t: Throwable => Left(t)
    }

  /**
    * Validate token.
    *
    * @param idToken Google id token.
    */
  private def validate(ws: WSClient, idToken: String)(implicit
      requestTimeout: FiniteDuration
  ): Future[WSResponse] = {
    ws.url(s"$requestUri/oauth2/$apiVersion/tokeninfo")
      .withRequestTimeout(requestTimeout)
      .addQueryStringParameters("id_token" -> idToken)
      .get()
  }

  /**
    * Get user details. (information)
    *
    * @param accessToken Access token.
    */
  private def userInfo(
      accessToken: String
  )(implicit ws: WSClient, requestTimeout: FiniteDuration): Future[WSResponse] =
    ws.url(s"$requestUri/oauth2/$apiVersion/userinfo")
      .withRequestTimeout(requestTimeout)
      .addQueryStringParameters("alt" -> "json")
      .addQueryStringParameters("access_token" -> accessToken)
      .get()

  /**
    * Google birthday is accessible only if the user made public the information.
    * The birthday format in JSON response is YYYY-MM-DD
    * Also a Google user could hide the year of birth. The year in response is substituted with 0000 and a
    * typical response in this case looks like 0000-MM-DD
    *
    * @param accessToken Access token
    * @param personId    Person ID
    */
  private def dayOfBirth(accessToken: String, personId: String)(implicit
      ws: WSClient,
      requestTimeout: FiniteDuration
  ): Future[WSResponse] =
    ws.url(s"$requestUri/plus/$apiVersion/people/$personId")
      .withRequestTimeout(requestTimeout)
      .withHttpHeaders(HeaderNames.AUTHORIZATION -> s"Bearer $accessToken")
      .get()
}
