package repo.person

import mongo.MongoDaemon
import org.bson.conversions.Bson
import org.mongodb.scala.model.Filters.equal
import org.mongodb.scala.result._

import scala.concurrent._

sealed trait PersonDSL[F[_]] {
  def findPerson(id: Int): F[Option[Person]]

  def getPersons: F[Seq[Person]]

  def createPerson(person: Person): F[InsertOneResult]

  def deletePerson(id: Int): F[DeleteResult]

  def updatePerson(person: Person): F[UpdateResult]
}

class PersonRepo(mongo: MongoDaemon[Person])(implicit ec: ExecutionContext)
    extends PersonDSL[Future] {

  def idEqual(objectId: Int): Bson =
    equal("_id", objectId)

  // equal("_id", new ObjectId(objectId))

  def async[A](result: Either[Throwable, Future[A]]): Future[A] =
    result fold (th => Future.failed(th), identity)

  def getPersons: Future[Seq[Person]] =
    async(mongo.queryCollection { collection =>
      collection.find().toFuture()
    })

  def findPerson(id: Int): Future[Option[Person]] =
    async(mongo.queryCollection { collection =>
      collection.find(idEqual(id)).first().headOption()
    })

  def createPerson(person: Person): Future[InsertOneResult] =
    async(mongo.queryCollection { collection =>
      collection.insertOne(person).toFuture()
    })

  def deletePerson(id: Int): Future[DeleteResult] =
    async(mongo.queryCollection { collection =>
      collection.deleteOne(idEqual(id)).toFuture()
    })

  def updatePerson(person: Person): Future[UpdateResult] =
    async(mongo.queryCollection { collection =>
      collection.replaceOne(idEqual(person._id), person).toFuture()
    })
}

object PersonRepo {
  def apply(
      mongo: MongoDaemon[Person]
  )(implicit ec: ExecutionContext): PersonRepo =
    new PersonRepo(mongo)
}
