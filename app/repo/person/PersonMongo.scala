package repo.person

import com.typesafe.config.Config
import mongo.Mongo.client
import mongo.{MongoConfig, MongoDaemon}
import org.bson.codecs.configuration.CodecRegistries.{
  fromProviders,
  fromRegistries
}
import org.bson.codecs.configuration.CodecRegistry
import org.mongodb.scala.MongoClient.DEFAULT_CODEC_REGISTRY
import org.mongodb.scala.bson.codecs.Macros._

import scala.concurrent.ExecutionContext.Implicits.global

case class Person(
    _id: Int,
    name: String,
    age: Int,
    address: Option[Address] = None
)

case class Address(street: String, no: Int, zip: Int)

object PersonMongo {

  private def config(config: Config): MongoConfig = {
    val cfg = config.getConfig("mongo.db")
    val mongoClient = client(cfg)
    val dbName = cfg.getString("person.db")
    val collectionName = cfg.getString("person.table")
    val codecRegistry: CodecRegistry = fromRegistries(
      fromProviders(classOf[Person], classOf[Address]),
      DEFAULT_CODEC_REGISTRY
    )
    MongoConfig(mongoClient, dbName, collectionName, codecRegistry)
  }

  def repo(cfg: Config): PersonRepo =
    PersonRepo(MongoDaemon[Person](config(cfg)))
}
