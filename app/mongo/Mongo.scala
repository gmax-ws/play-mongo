package mongo

import com.typesafe.config.Config
import org.bson.codecs.configuration.CodecRegistry
import org.mongodb.scala._

import scala.reflect.ClassTag
import scala.util.Try

case class MongoConfig(
    mongoClient: MongoClient,
    dbName: String,
    collectionName: String,
    codecRegistry: CodecRegistry
)

case class MongoDaemon[A: ClassTag](cfg: MongoConfig) {

  def queryCollection[R](query: MongoCollection[A] => R): Either[Throwable, R] =
    Try {
      val database: MongoDatabase = cfg.mongoClient
        .getDatabase(cfg.dbName)
        .withCodecRegistry(cfg.codecRegistry)
      val collection: MongoCollection[A] =
        database.getCollection(cfg.collectionName)
      query(collection)
    }.toEither
}

object Mongo {

  def client(cfg: Config): MongoClient = {
    val username = cfg.getString("username")
    val password = cfg.getString("password")
    val authSource = cfg.getString("authSource")
    val host = cfg.getString("host")
    val port = cfg.getInt("port")
    val authMechanism = cfg.getString("authMechanism")
    MongoClient(
      s"mongodb://$username:$password@$host:$port/?authSource=$authSource&authMechanism=$authMechanism"
    )
  }
}
