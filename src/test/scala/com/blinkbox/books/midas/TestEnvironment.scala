package com.blinkbox.books.midas

import java.net.URL

import akka.actor.{ActorRefFactory, ActorSystem}
import com.blinkbox.books.test.MockitoSyrup
import org.mockito.Matchers._
import org.mockito.Mockito._
import spray.client.pipelining._
import spray.http._

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.duration._
import scala.concurrent.{ExecutionContext, Future}

trait TestEnvironment extends MockitoSyrup {

  implicit val system = ActorSystem("test-system")

  val appConfig = MidasConfig(new URL("https://myfavoritewebsite.com"), 1.second)
  val client = new TestClient(appConfig)
  val mockSendReceive = mock[SendReceive]
  val service: Any

  val validToken = SsoAccessToken("validToken")
  val invalidToken = SsoAccessToken("invalidToken")

  def provideJsonResponse(statusCode: StatusCode, content: String): Unit = {
    val resp = HttpResponse(statusCode, HttpEntity(MediaTypes.`application/json`, content))
    when(mockSendReceive.apply(any[HttpRequest])).thenReturn(Future.successful(resp))
  }

  def provideResponse(statusCode: StatusCode) = {
    val resp = HttpResponse(statusCode)
    when(mockSendReceive.apply(any[HttpRequest])).thenReturn(Future.successful(resp))
  }

  def provideErrorResponse(e: Throwable): Unit =
    when(mockSendReceive.apply(any[HttpRequest])).thenReturn(Future.failed(e))

  class TestClient(val config: MidasConfig)(implicit val ec: ExecutionContext, val system: ActorSystem) extends SprayClient {
    override def doSendReceive(implicit refFactory: ActorRefFactory, ec: ExecutionContext): SendReceive = mockSendReceive
  }
}

class AccountServiceEnvironment extends TestEnvironment {
  val service = new DefaultAccountCreditService(appConfig, client)
}

class ClubcardServiceEnvironment extends TestEnvironment {
  val service = new DefaultClubcardService(appConfig, client)
  val validCardNumber = ClubcardNumber("634004553765751581")
}