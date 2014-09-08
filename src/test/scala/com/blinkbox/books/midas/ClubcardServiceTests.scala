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
import spray.client.pipelining.SendReceive
import spray.http.HttpHeaders.Authorization
import spray.http._
import spray.httpx.RequestBuilding.Get
import spray.httpx.unmarshalling.Deserializer

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.duration._
import scala.concurrent.{ExecutionContext, Future}

@RunWith(classOf[JUnitRunner])
class ClubcardServiceTests extends FlatSpec with ScalaFutures with FailHelper with MockitoSyrup {

  val appConfig = MidasConfig(new URL("https://myfavoritewebsite.com"), 1.second)
  implicit def deserializer = any[Deserializer[HttpResponse, Balance]]
  implicit val system = ActorSystem("test-system")

  // Settings for whenReady/Waiter. We override the default values because the first call to the mock
  // Feature service takes longer than the default values.
  implicit val defaultPatience = PatienceConfig(timeout = scaled(Span(5, Seconds)), interval = scaled(Span(5, Millis)))

  class TestEnvironment {

    val client = new TestClient(appConfig)
    val mockSendReceive = mock[SendReceive]
    val service = new DefaultClubcardService(appConfig, client)

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

  "A clubcard service client" should "return a valid clubcard details" in new TestEnvironment {
    provideResponse(StatusCodes.OK, """{
        |"DisplayName":"testName",
        |"CardNumber":"634004553765751581",
        |"IsPrimaryCard":true,
        |"IsPrivilegeCard":false
        |}""".stripMargin)

    whenReady(service.clubcardDetails(ClubcardNumber("634004553765751581"))(new SsoAccessToken("token"))) { res =>
      assert(res == Clubcard(ClubcardNumber("634004553765751581"), "testName", primary = true))
      verify(mockSendReceive).apply(Get(s"${appConfig.url}/api/wallet/clubcards/634004553765751581").withHeaders(Authorization(OAuth2BearerToken("token"))))
    }
  }
}
