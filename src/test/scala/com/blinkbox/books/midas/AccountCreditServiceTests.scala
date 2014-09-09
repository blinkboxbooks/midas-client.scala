package com.blinkbox.books.midas

import com.blinkbox.books.test.FailHelper
import org.junit.runner.RunWith
import org.mockito.Mockito._
import org.scalatest.FlatSpec
import org.scalatest.concurrent.ScalaFutures
import org.scalatest.junit.JUnitRunner
import org.scalatest.time.{Millis, Seconds, Span}
import spray.can.Http.ConnectionException
import spray.http.HttpHeaders.Authorization
import spray.http._
import spray.httpx.RequestBuilding.Get

import scala.concurrent.ExecutionContext.Implicits.global

import spray.http.StatusCodes._

class AccountServiceEnvironment extends TestEnvironment {
  val service = new DefaultAccountCreditService(appConfig, client)
}

@RunWith(classOf[JUnitRunner])
class AccountCreditServiceTests extends FlatSpec with ScalaFutures with FailHelper {

  // Settings for whenReady/Waiter.
  implicit val defaultPatience = PatienceConfig(timeout = scaled(Span(5, Seconds)), interval = scaled(Span(25, Millis)))

  "An account service client" should "return a valid account balance" in new AccountServiceEnvironment {
    provideJsonResponse(OK, """{"Balance":10.0}""")

    whenReady(service.balance(validToken)) { res =>
      assert(res == Balance(BigDecimal("10.0")))
      verify(mockSendReceive).apply(Get(s"${appConfig.url}/api/wallet/balance").withHeaders(Authorization(OAuth2BearerToken(validToken.value))))
    }
  }

  it should "should pass on connection exceptions that happen during requests" in new AccountServiceEnvironment {
    provideErrorResponse(new ConnectionException("message"))
    failingWith[ConnectionException](service.balance(validToken))
  }

  it should "throw an UnauthorizedException when getting account balance with invalid access token" in new AccountServiceEnvironment {
    provideJsonResponse(Unauthorized, """{"Message":"Token is invalid or expired"}""")

    val ex = failingWith[UnauthorizedException](service.balance(validToken))
    assert(ex.error == ErrorMessage("Token is invalid or expired"))
    assert(ex.challenge == HttpChallenge("Bearer", realm = "", Map("not" -> "available")))
  }

  it should "throw a NotFoundException when getting account balance for a user that has no wallet" in new AccountServiceEnvironment {
    provideJsonResponse(NotFound, """{"Message": "Wallet not found"}""")

    val ex = failingWith[NotFoundException](service.balance(validToken))
    assert(ex.error == Some(ErrorMessage("Wallet not found")))
  }

  it should "return the response content when an error response can't be parsed" in new AccountServiceEnvironment {
    provideJsonResponse(NotFound, "This is not a valid JSON error message")

    val ex = failingWith[NotFoundException](service.balance(validToken))
    assert(ex.error == Some(ErrorMessage("Cannot parse error message: This is not a valid JSON error message")))
  }
}
