package com.blinkbox.books.midas

import akka.actor.{ActorRefFactory, ActorSystem}
import akka.util.Timeout
import org.json4s.FieldSerializer.{renameFrom, renameTo}
import org.json4s.{DefaultFormats, FieldSerializer}
import spray.client.pipelining._
import spray.http._
import spray.http.StatusCodes._
import spray.httpx.unmarshalling._
import spray.httpx.{Json4sJacksonSupport, UnsuccessfulResponseException}

import scala.concurrent.{ExecutionContext, Future}

/**
 * Basic API for Spray client requests.
 */
trait Client {
  def unitRequest(req: HttpRequest, credentials: Option[HttpCredentials]): Future[Unit]
  def dataRequest[T : FromResponseUnmarshaller](req: HttpRequest, credentials: Option[HttpCredentials]): Future[T]
}

/**
 * Trait that provides common implementation of request handling and response parsing.
 */
trait SprayClient extends Client with Json4sJacksonSupport {
  val config: MidasConfig
  val system: ActorSystem
  val ec: ExecutionContext

  implicit lazy val timeout = Timeout(config.timeout)
  implicit lazy val sys = system
  implicit lazy val executionContext = ec

  implicit def json4sJacksonFormats = DefaultFormats + ErrorMessage.errorMessageSerializer

  protected def doSendReceive(implicit refFactory: ActorRefFactory, ec: ExecutionContext): SendReceive = sendReceive(refFactory, ec)

  protected lazy val unitIfSuccessful = { resp: HttpResponse =>
    if (resp.status.isSuccess) () else throw new UnsuccessfulResponseException(resp)
  }

  protected def basePipeline(credentials: Option[HttpCredentials]): SendReceive = credentials match {
    case Some(creds) => addCredentials(creds) ~> doSendReceive
    case None => doSendReceive
  }

  protected def unitPipeline(credentials: Option[HttpCredentials]) = basePipeline(credentials) ~> unitIfSuccessful

  protected def dataPipeline[T : FromResponseUnmarshaller](credentials: Option[HttpCredentials]) =
    basePipeline(credentials) ~> unmarshal[T]

  override def unitRequest(req: HttpRequest, credentials: Option[HttpCredentials]): Future[Unit] = unitPipeline(credentials)(req)

  override def dataRequest[T : FromResponseUnmarshaller](req: HttpRequest, credentials: Option[HttpCredentials]): Future[T] =
    dataPipeline(credentials).apply(req)
      .transform(identity, exceptionTransformer)

  def exceptionTransformer: Throwable => Throwable = {
    case ex: UnsuccessfulResponseException if ex.response.status == Unauthorized =>
      val tmpChallenge = HttpChallenge(scheme = "Bearer", realm = "", Map("not" -> "available")) // TODO: change once Midas adds WWW-Authenticate headers
      new UnauthorizedException(parseErrorMessage(ex.response.entity), tmpChallenge, ex)
    case ex: UnsuccessfulResponseException if ex.response.status == NotFound =>
      new NotFoundException(parseErrorMessage(ex.response.entity), ex)
    case other => other
  }

  private def parseErrorMessage(entity: HttpEntity): ErrorMessage =
    entity.as[ErrorMessage].fold[ErrorMessage](err => ErrorMessage(s"Cannot parse error message: ${entity.asString}"), identity)
}

/**
 * Concrete implementation of Midas client.
 */
class DefaultClient(val config: MidasConfig)(implicit val ec: ExecutionContext, val system: ActorSystem) extends SprayClient

/**
 * Common error message response for MIDAS responses.
 */
case class ErrorMessage(message: String)

object ErrorMessage {
  val errorMessageSerializer = FieldSerializer[ErrorMessage](renameTo("message", "Message"), renameFrom("Message", "message"))
}

// Exceptions raised by client API.
class NotFoundException(val error: ErrorMessage, cause: Throwable = null) extends RuntimeException(error.toString, cause)
class UnauthorizedException(val error: ErrorMessage, val challenge: HttpChallenge, cause: Throwable = null) extends RuntimeException(error.toString, cause)
