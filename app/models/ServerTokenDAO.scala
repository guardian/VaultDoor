package models

import akka.actor.ActorSystem
import akka.stream.{ActorMaterializer, Materializer}
import javax.inject.Inject
import play.api.Configuration

import scala.concurrent.{ExecutionContext, Future}

trait ServerTokenDAO {
  protected implicit val actorSystem:ActorSystem
  protected implicit val mat:Materializer
  protected implicit val ec:ExecutionContext = actorSystem.dispatcher

  def put(entry:ServerTokenEntry, expiresIn:Int):Future[Boolean]

  def get(tokenValue:String):Future[Option[ServerTokenEntry]]
}
