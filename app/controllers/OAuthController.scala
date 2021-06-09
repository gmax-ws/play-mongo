package controllers

import controllers.PersonJsonCodecs.rsp
import oauth.{KeycloakIntegration, OAuth2Facebook, OAuth2Google}
import play.api.data.Form
import play.api.data.Forms.{text, tuple}
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
    Action.async { implicit request =>
      val params = request.body.asFormUrlEncoded.getOrElse(Map.empty)
      val accessToken = params.getOrElse("access_token", Seq("?")).head
      val idToken = params.getOrElse("id_token", Seq("?")).head
      for {
        result <- OAuth2Google(accessToken, idToken)
      } yield result match {
        case Right(m) => Ok(rsp("OK", m))
        case Left(e)  => BadRequest(rsp(e.getMessage))
      }
    }

  def facebook: Action[AnyContent] =
    Action.async { implicit request =>
      val params = request.body.asFormUrlEncoded.getOrElse(Map.empty)
      val accessToken = params.getOrElse("access_token", Seq("?")).head
      val userId = params.getOrElse("user_id", Seq("?")).head
      for {
        result <- OAuth2Facebook(accessToken, userId)
      } yield result match {
        case Right(m) => Ok(rsp("OK", m))
        case Left(e)  => BadRequest(rsp(e.getMessage))
      }
    }

  def getToken: Action[AnyContent] =
    Action.async { implicit request =>
      for {
        response <- KeycloakIntegration.getToken
      } yield {
        if (response.status == OK)
          Ok(response.body)
        else
          InternalServerError(rsp(response.body))
      }
    }

  private val loginForm = Form(
    tuple(
      "username" -> text,
      "password" -> text
    )
  )

  def login: Action[AnyContent] =
    Action.async { implicit request =>
      val (username, password) = loginForm.bindFromRequest().get
      KeycloakIntegration.authenticate(username, password) map { response =>
        if (response.status == OK)
          Ok(response.body)
        else
          InternalServerError(rsp(response.body))
      }
    }
}
