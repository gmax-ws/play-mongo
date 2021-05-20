package services

import org.mongodb.scala.result._
import repo.person.{Person, PersonRepo}

import scala.concurrent.Future

case class PersonServices(repo: PersonRepo) {

  def getPersons: Future[Seq[Person]] =
    repo.getPersons

  def findPerson(id: Int): Future[Option[Person]] =
    repo.findPerson(id)

  def createPerson(person: Person): Future[InsertOneResult] =
    repo.createPerson(person)

  def updatePerson(person: Person): Future[UpdateResult] =
    repo.updatePerson(person)

  def deletePerson(id: Int): Future[DeleteResult] =
    repo.deletePerson(id)
}
