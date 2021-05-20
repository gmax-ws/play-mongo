package controllers

import oauth.{KeycloakIntegration, OAuth2Facebook, OAuth2Google}
import play.api.libs.json.Json
import play.api.libs.ws.WSClient
import play.api.mvc.{
  AbstractController,
  Action,
  AnyContent,
  ControllerComponents
}

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.duration.FiniteDuration

class OAuthController(
    cc: ControllerComponents
)(implicit ws: WSClient, requestTimeout: FiniteDuration)
    extends AbstractController(cc) {

  def google: Action[AnyContent] =
    Action.async { request =>
      val params = request.body.asFormUrlEncoded.getOrElse(Map.empty)
      val accessToken = params.getOrElse("access_token", Seq("?")).head
      val idToken = params.getOrElse("id_token", Seq("?")).head
      for {
        result <- OAuth2Google(accessToken, idToken)
      } yield result match {
        case Right(m) => Ok(Json.toJson(m))
        case Left(e)  => BadRequest(Json.toJson(e.getMessage))
      }
    }

  def facebook: Action[AnyContent] =
    Action.async { request =>
      val params = request.body.asFormUrlEncoded.getOrElse(Map.empty)
      val accessToken = params.getOrElse("access_token", Seq("?")).head
      val userId = params.getOrElse("user_id", Seq("?")).head
      for {
        result <- OAuth2Facebook(accessToken, userId)
      } yield result match {
        case Right(m) => Ok(Json.toJson(m))
        case Left(e)  => BadRequest(Json.toJson(e.getMessage))
      }
    }

  def getToken: Action[AnyContent] =
    Action.async {
      for {
        response <- KeycloakIntegration.getToken
      } yield Ok(response.body)
    }
}
