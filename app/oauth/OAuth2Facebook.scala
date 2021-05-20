package oauth

import play.api.http.Status
import play.api.libs.ws.{WSClient, WSResponse}
import play.shaded.ahc.org.asynchttpclient.Response

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future
import scala.concurrent.duration.FiniteDuration

object OAuth2Facebook {

  /** Request URI */
  private val requestUri = "https://graph.facebook.com"

  /** API version */
  private val apiVersion = "v10.0"

  /**
    * Facebook OAuth2 implementation
    *
    * @param accessToken Access token
    * @param userId      User ID
    * @return User details
    */
  def apply(
      accessToken: String,
      userId: String
  )(implicit
      ws: WSClient,
      requestTimeout: FiniteDuration
  ): Future[Either[Throwable, Map[String, Option[String]]]] =
    (for {
      valid <- userProfile(accessToken)
      picture <- profilePicture(userId) if valid.status == Status.OK
    } yield {
      val json = valid.json
      val pict =
        if (picture.status == Status.OK)
          Option(picture.underlying[Response].getUri.toString)
        else None
      Right(
        Map(
          "user" -> (json \ "id").asOpt[String],
          "name" -> (json \ "name").asOpt[String],
          "email" -> (json \ "email").asOpt[String],
          "gender" -> (json \ "gender").asOpt[String],
          "picture" -> pict,
          "dob" -> (json \ "birthday").asOpt[String]
        )
      )
    }) recover {
      case t: Throwable => Left(t)
    }

  /**
    * Validate Facebook access token
    *
    * @param accessToken Access token
    */
  private def userProfile(
      accessToken: String
  )(implicit ws: WSClient, requestTimeout: FiniteDuration): Future[WSResponse] = {
    val fields = "id,name,email,picture,gender,birthday"
    ws.url(s"$requestUri/$apiVersion/me")
      .withRequestTimeout(requestTimeout)
      .addQueryStringParameters("access_token" -> accessToken)
      .addQueryStringParameters("fields" -> fields)
      .get()
  }

  /**
    * Get user profile large picture.
    *
    * @param userId Facebook user ID
    */
  private def profilePicture(
      userId: String
  )(implicit ws: WSClient, requestTimeout: FiniteDuration): Future[WSResponse] = {
    ws.url(s"$requestUri/$apiVersion/$userId/picture")
      .withRequestTimeout(requestTimeout)
      .addQueryStringParameters("type" -> "large")
      .get()
  }
}
