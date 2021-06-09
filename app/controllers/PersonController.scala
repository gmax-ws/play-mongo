package controllers

import oauth.KeycloakIntegration.withAuthorization
import play.api.libs.json._
import play.api.libs.ws.WSClient
import play.api.mvc.{
  AbstractController,
  Action,
  AnyContent,
  ControllerComponents,
  RequestHeader
}
import repo.person.{Address, Person, PersonRepo}

import java.time.Instant
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future
import scala.concurrent.duration.FiniteDuration

object PersonJsonCodecs {
  import play.api.libs.json._

  implicit val addressWrites: OWrites[Address] = Json.writes[Address]
  implicit val addressFormat: OFormat[Address] = Json.format[Address]
  implicit val personWrites: OWrites[Person] = Json.writes[Person]
  implicit val personFormat: OFormat[Person] = Json.format[Person]

  def timestamp: String =
    DateTimeFormatter.ISO_INSTANT.format(
      Instant.now().truncatedTo(ChronoUnit.SECONDS)
    )

  def rsp(message: String)(implicit request: RequestHeader): JsObject =
    JsObject(
      Seq(
        "timestamp" -> JsString(timestamp),
        "version" -> JsString(request.version),
        "host" -> JsString(request.host),
        "uri" -> JsString(request.uri),
        "method" -> JsString(request.method),
        "contentType" -> JsString(request.contentType.getOrElse("")),
        "hasBody" -> JsBoolean(request.hasBody),
        "message" -> JsString(message)
      )
    )

  def rsp(message: String, data: JsValue)(implicit
      request: RequestHeader
  ): JsObject =
    rsp(message) + ("data", data)

  def rsp(message: String, data: JsError)(implicit
      request: RequestHeader
  ): JsObject =
    rsp(message) + ("data", JsError.toJson(data))

  def rsp(message: String, data: Person)(implicit
      request: RequestHeader
  ): JsObject =
    rsp(message, Json.toJson(data))

  def rsp(message: String, data: Seq[Person])(implicit
      request: RequestHeader
  ): JsObject =
    rsp(message, Json.toJson(data))

  def rsp(message: String, data: Map[String, Option[String]])(implicit
      request: RequestHeader
  ): JsObject =
    rsp(message, Json.toJson(data))
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
        } yield Ok(rsp("success", persons))
      }
    }

  def findPerson(id: Int): Action[AnyContent] =
    Action.async { implicit request =>
      withAuthorization("ROLE_USER") {
        personRepo.findPerson(id).map { person =>
          person.fold(NotFound(rsp(s"Not found $id")))(p =>
            Ok(rsp("success", p))
          )
        }
      }
    }

  def createPerson: Action[AnyContent] =
    Action.async { implicit request =>
      withAuthorization("ROLE_WRITE", "ROLE_TEMPLATE") {
        request.body.asJson.fold(
          Future.successful(BadRequest(rsp(request.body.asText.getOrElse("Missing input data!"))))
        )(
          Json.fromJson[Person](_) match {
            case JsSuccess(person: Person, _: JsPath) =>
              personRepo.createPerson(person) map { _ =>
                Created(rsp("created", person))
              }
            case e @ JsError(_) =>
              Future.successful(BadRequest(rsp("JSON error!", e)))
          }
        )
      }
    }

  def updatePerson(): Action[AnyContent] =
    Action.async { implicit request =>
      withAuthorization("ROLE_WRITE", "ROLE_TEMPLATE") {
        request.body.asJson.fold(
          Future.successful(BadRequest(rsp(request.body.asText.getOrElse("Missing input data!"))))
        )(p =>
          Json.fromJson[Person](p) match {
            case JsSuccess(person: Person, _: JsPath) =>
              personRepo.updatePerson(person) map { result =>
                if (result.getModifiedCount > 0) Ok(rsp("updated", person))
                else
                  NotFound(
                    rsp(s"Person ${person._id} not found!")
                  )
              }
            case e @ JsError(_) =>
              Future.successful(BadRequest(rsp("JSON error!", e)))
          }
        )
      }
    }

  def deletePerson(id: Int): Action[AnyContent] = {
    Action.async { implicit request =>
      withAuthorization("ROLE_TEMPLATE") {
        for {
          result <- personRepo.deletePerson(id)
        } yield
          if (result.getDeletedCount > 0)
            Ok(rsp(s"$id deleted..."))
          else NotFound(rsp(s"$id not found..."))
      }
    }
  }
}
