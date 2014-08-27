package com.blinkbox.books.midas

import akka.actor.{ActorRefFactory, ActorSystem}
import akka.util.Timeout
import com.blinkbox.books.config.Configuration
import com.typesafe.scalalogging.slf4j.StrictLogging
import org.json4s.{FieldSerializer, DefaultFormats, Formats}
import org.json4s.FieldSerializer.{renameTo, renameFrom}
import spray.client.pipelining._
import spray.http.{OAuth2BearerToken, HttpResponse, HttpRequest, HttpCredentials}
import spray.httpx.{Json4sJacksonSupport, UnsuccessfulResponseException}
import spray.httpx.unmarshalling._

import scala.concurrent.{ExecutionContext, Future}

trait Client {
  def unitRequest(req: HttpRequest, credentials: Option[HttpCredentials]): Future[Unit]
  def dataRequest[T : FromResponseUnmarshaller](req: HttpRequest, credentials: Option[HttpCredentials]): Future[T]
}

trait SprayClient extends Client {
  val config: MidasConfig
  val system: ActorSystem
  val ec: ExecutionContext

  implicit lazy val _timeout = Timeout(config.timeout)
  implicit lazy val _system = system
  implicit lazy val _ec = ec

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
    dataPipeline(credentials).apply(req) // TODO: why do I need apply here?
}

class DefaultClient(val config: MidasConfig)(implicit val ec: ExecutionContext, val system: ActorSystem) extends SprayClient

case class Balance(amount: BigDecimal)

trait AccountCreditService {
  /**
   *
   * @param accessToken SSO access token
   * @return a future with a successful result or an Exception. Any exception can be thrown; known exceptions are:
   *         ConnectionException
   *         NotFoundException
   *         UnauthorizedException
   */
  def balance(implicit accessToken: String): Future[Balance]
}

class DefaultAccountCreditService(config: MidasConfig, client: Client)(implicit ec: ExecutionContext)
  extends AccountCreditService with Json4sJacksonSupport with StrictLogging {

  val balanceSerializer = FieldSerializer[Balance](renameTo("amount", "Balance"), renameFrom("Balance", "amount"))

  override implicit def json4sJacksonFormats: Formats = DefaultFormats + balanceSerializer

  val serviceBase = config.url

  override def balance(implicit accessToken: String): Future[Balance] = {
    val req = Get(s"$serviceBase/api/wallet/balance")
    client.dataRequest[Balance](req, Some(OAuth2BearerToken(accessToken))).transform(identity, exceptionTransformer)
  }

  def exceptionTransformer: Throwable => Throwable = {
    case ex => ex
  }

}

object TestApp extends App with Configuration {
  val appConfig = MidasConfig(config)

  implicit val system = ActorSystem("test")
  implicit val ec = system.dispatcher

  val service = new DefaultAccountCreditService(appConfig, new DefaultClient(appConfig))

  val accessToken = "eyJhbGciOiJFUzI1NiJ9.eyJzY3AiOlsic3NvOm1vdmllcyJdLCJleHAiOjE0MDkxNTY2MTAsInN1YiI6IjU2MUMxQzgwLUQxNjYtNEZDMy04NjY1LTBEMThBODUzODVGNiIsInJpZCI6IjFGRjczRjMzLUZCMEYtNDQ2NC05NkZGLTA0OEVFQjgzQ0QyRCIsImxuayI6W10sInNydiI6Im1vdmllcyIsInJvbCI6W10sInRrdCI6ImFjY2VzcyIsImlhdCI6MTQwOTE1NDgxMH0.VnhuEFXTH_2AeZMqC8KfoEJl_1gw26SJ-OMaTm095-Rx2sHzEKPzuymcnNGYl56GJU2mf3I8EVUZw3PPsKLgOw"

  service.balance(accessToken) onComplete {
    case res => println(res)
  }
}


