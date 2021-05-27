package controllers

import oauth.KeycloakIntegration.withAuthorization
import play.api.libs.json._
import play.api.libs.ws.WSClient
import play.api.mvc.{
  AbstractController,
  Action,
  AnyContent,
  ControllerComponents
}
import repo.person.{Address, Person, PersonRepo}

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future
import scala.concurrent.duration.FiniteDuration

object PersonJsonCodecs {
  implicit val addressWrites: OWrites[Address] = Json.writes[Address]
  implicit val addressFormat: OFormat[Address] = Json.format[Address]
  implicit val personWrites: OWrites[Person] = Json.writes[Person]
  implicit val personFormat: OFormat[Person] = Json.format[Person]
}

class PersonController(
    cc: ControllerComponents,
    personRepo: PersonRepo
)(implicit ws: WSClient, requestTimeout: FiniteDuration)
    extends AbstractController(cc) {
  import PersonJsonCodecs._

  def getPersons: Action[AnyContent] =
    Action.async { implicit request =>
      withAuthorization("ROLE_USER") {
        for {
          persons <- personRepo.getPersons
        } yield Ok(Json.toJson(persons))
      }
    }

  def findPerson(id: Int): Action[AnyContent] =
    Action.async { implicit request =>
      withAuthorization("ROLE_USER") {
        personRepo.findPerson(id).map { person =>
          person.fold(NotFound(s"Not found $id"))(p => Ok(Json.toJson(p)))
        }
      }
    }

  def createPerson: Action[AnyContent] =
    Action.async { implicit request =>
      withAuthorization("ROLE_WRITE", "ROLE_TEMPLATE") {
        request.body.asJson.fold(
          Future.successful(BadRequest(request.body.asText.getOrElse("?")))
        )(
          Json.fromJson[Person](_) match {
            case JsSuccess(person: Person, _: JsPath) =>
              personRepo.createPerson(person) map { _ =>
                Created(Json.toJson(person))
              }
            case e @ JsError(_) =>
              Future.successful(BadRequest(JsError.toJson(e)))
          }
        )
      }
    }

  def updatePerson(): Action[AnyContent] =
    Action.async { implicit request =>
      withAuthorization("ROLE_WRITE", "ROLE_TEMPLATE") {
        request.body.asJson.fold(
          Future.successful(BadRequest(request.body.asText.getOrElse("?")))
        )(p =>
          Json.fromJson[Person](p) match {
            case JsSuccess(person: Person, _: JsPath) =>
              personRepo.updatePerson(person) map { _ =>
                Ok(Json.toJson(person))
              }
            case e @ JsError(_) =>
              Future.successful(BadRequest(JsError.toJson(e)))
          }
        )
      }
    }

  def deletePerson(id: Int): Action[AnyContent] = {
    Action.async { implicit request =>
      withAuthorization("ROLE_TEMPLATE") {
        (for {
          result <- personRepo.deletePerson(id)
        } yield
          if (result.getDeletedCount > 0) Ok(s"$id deleted...")
          else NotFound(s"$id not found...")).recover {
          case e: Exception => InternalServerError(e.getMessage)
        }
      }
    }
  }
}
