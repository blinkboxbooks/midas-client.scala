package com.blinkbox.books.midas

import java.net.URL

import akka.actor.{ActorRefFactory, ActorSystem}
import com.blinkbox.books.test.{FailHelper, MockitoSyrup}
import org.junit.runner.RunWith
import org.mockito.Matchers._
import org.mockito.Mockito._
import org.scalatest.FlatSpec
import org.scalatest.concurrent.ScalaFutures
import org.scalatest.junit.JUnitRunner
import org.scalatest.time.{Millis, Seconds, Span}
import spray.can.Http.ConnectionException
import spray.client.pipelining._
import spray.http.HttpHeaders.Authorization
import spray.http._
import spray.httpx.RequestBuilding.Get
import spray.httpx.unmarshalling.Deserializer

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.duration._
import scala.concurrent.{ExecutionContext, Future}

@RunWith(classOf[JUnitRunner])
class AccountCreditServiceTests extends FlatSpec with ScalaFutures with FailHelper with MockitoSyrup {

  val appConfig = MidasConfig(new URL("https://myfavoritewebsite.com"), 1.second)
  implicit def deserializer = any[Deserializer[HttpResponse, Balance]]
  implicit val system = ActorSystem("test-system")

  // Settings for whenReady/Waiter. We override the default values because the first call to the mock
  // Feature service takes longer than the default values.
  implicit val defaultPatience = PatienceConfig(timeout = scaled(Span(5, Seconds)), interval = scaled(Span(5, Millis)))

  class TestEnvironment {

    val client = new TestClient(appConfig)
    val mockSendReceive = mock[SendReceive]
    val service = new DefaultAccountCreditService(appConfig, client)

    def provideResponse(statusCode: StatusCode, content: String): Unit = {
      val resp = HttpResponse(statusCode, HttpEntity(MediaTypes.`application/json`, content))
      when(mockSendReceive.apply(any[HttpRequest])).thenReturn(Future.successful(resp))
    }

    def provideErrorResponse(e: Throwable): Unit =
      when(mockSendReceive.apply(any[HttpRequest])).thenReturn(Future.failed(e))

    class TestClient(val config: MidasConfig)(implicit val ec: ExecutionContext, val system: ActorSystem) extends SprayClient {
      override def doSendReceive(implicit refFactory: ActorRefFactory, ec: ExecutionContext): SendReceive = mockSendReceive
    }
  }

  "An account service client" should "return a valid account balance" in new TestEnvironment {
    provideResponse(StatusCodes.OK, """{"Balance":10.0}""")

    whenReady(service.balance(new SsoAccessToken("token"))) { res =>
      assert(res == Balance(BigDecimal("10.0")))
      verify(mockSendReceive).apply(Get(s"${appConfig.url}/api/wallet/balance").withHeaders(Authorization(OAuth2BearerToken("token"))))
    }
  }

  it should "should pass on connection exceptions that happen during requests" in new TestEnvironment {
    provideErrorResponse(new ConnectionException("message"))
    failingWith[ConnectionException](service.balance(new SsoAccessToken("token")))
  }

  it should "throw an UnauthorizedException when getting account balance with invalid access token" in new TestEnvironment {
    provideResponse(StatusCodes.Unauthorized, """{"Message":"Token is invalid or expired"}""")

    val ex = failingWith[UnauthorizedException](service.balance(new SsoAccessToken("token")))
    assert(ex.error == ErrorMessage("Token is invalid or expired"))
    assert(ex.challenge == HttpChallenge("Bearer", realm = "", Map("not" -> "available")))
  }

  it should "throw a NotFoundException when getting account balance for a user that has no wallet" in new TestEnvironment {
    provideResponse(StatusCodes.NotFound, """{"Message": "Wallet not found"}""")

    val ex = failingWith[NotFoundException](service.balance(new SsoAccessToken("token")))
    assert(ex.error == ErrorMessage("Wallet not found"))
  }

  it should "return the response content when an error response can't be parsed" in new TestEnvironment {
    provideResponse(StatusCodes.NotFound, "This is not a valid JSON error message")

    val ex = failingWith[NotFoundException](service.balance(new SsoAccessToken("token")))
    assert(ex.error == ErrorMessage("Cannot parse error message: This is not a valid JSON error message"))
  }
}
