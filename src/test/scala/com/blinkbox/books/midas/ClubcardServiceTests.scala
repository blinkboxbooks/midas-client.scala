package com.blinkbox.books.midas

import com.blinkbox.books.test.{FailHelper, MockitoSyrup}
import org.junit.runner.RunWith
import org.mockito.ArgumentCaptor
import org.mockito.Mockito._
import org.scalatest.FlatSpec
import org.scalatest.concurrent.ScalaFutures
import org.scalatest.junit.JUnitRunner
import org.scalatest.time.{Millis, Seconds, Span}
import spray.can.Http.ConnectionException
import spray.http.HttpHeaders.Authorization
import spray.http.StatusCodes._
import spray.http._
import spray.httpx.RequestBuilding.Get

import scala.concurrent.ExecutionContext.Implicits.global

class ClubcardServiceEnvironment extends TestEnvironment {
  val service = new DefaultClubcardService(appConfig, client)
  val validCardNumber = "634004553765751581"
}

@RunWith(classOf[JUnitRunner])
class ClubcardServiceTests extends FlatSpec with ScalaFutures with FailHelper with MockitoSyrup {

  // Settings for whenReady/Waiter.
  implicit val defaultPatience = PatienceConfig(timeout = scaled(Span(5, Seconds)), interval = scaled(Span(5, Millis)))


  "A clubcard service client" should "return a valid clubcard details" in new ClubcardServiceEnvironment {
    provideJsonResponse(OK, s"""{"DisplayName":"testName","CardNumber":"$validCardNumber","IsPrimaryCard":true,"IsPrivilegeCard":false}""")

    whenReady(service.clubcardDetails(validCardNumber)(validToken)) { res =>
      assert(res == Clubcard(validCardNumber, "testName", isPrimaryCard = true, isPrivilegeCard = false))
      verify(mockSendReceive).apply(Get(s"${appConfig.url}/api/wallet/clubcards/$validCardNumber").withHeaders(Authorization(OAuth2BearerToken(validToken.value))))
    }
  }

  it should "should pass on connection exceptions that happen during requests" in new ClubcardServiceEnvironment {
    provideErrorResponse(new ConnectionException("message"))
    failingWith[ConnectionException](service.clubcardDetails(validCardNumber)(validToken))
  }

  it should "throw an UnauthorizedException when getting clubcard details with invalid access token" in new ClubcardServiceEnvironment {
    provideJsonResponse(Unauthorized, """{"Message":"Token is invalid or expired"}""")

    val ex = failingWith[UnauthorizedException](service.clubcardDetails(validCardNumber)(invalidToken))
    assert(ex.error == ErrorMessage("Token is invalid or expired"))
    assert(ex.challenge == HttpChallenge("Bearer", realm = "", Map("not" -> "available")))
  }

  it should "throw NotFoundException when getting clubcard details for a user that has no wallet" in new ClubcardServiceEnvironment {
    provideJsonResponse(NotFound, """{"Message": "Wallet not found"}""")

    val ex = failingWith[NotFoundException](service.clubcardDetails(validCardNumber)(validToken))
    assert(ex.error == Some(ErrorMessage("Wallet not found")))
  }

  it should "throw NotFoundException when getting clubcard details that is not in user's wallet" in new ClubcardServiceEnvironment {
    provideResponse(NotFound)

    val ex = failingWith[NotFoundException](service.clubcardDetails(validCardNumber)(validToken))
    assert(ex.error == None)
  }

  it should "return a primary clubcard if there is at least one clubcard in user's wallet" in new ClubcardServiceEnvironment {
    provideJsonResponse(StatusCodes.OK, s"""{"DisplayName":"primary card","CardNumber":"$validCardNumber","IsPrimaryCard":true,"IsPrivilegeCard":false}""")

    whenReady(service.primaryClubcard()(validToken)) { res =>
      assert(res == Clubcard(validCardNumber, "primary card", isPrimaryCard = true, isPrivilegeCard = false))
      verify(mockSendReceive).apply(Get(s"${appConfig.url}/api/wallet/clubcards/primary").withHeaders(Authorization(OAuth2BearerToken(validToken.value))))
    }
  }

  it should "throw NotFoundException when getting primary clubcard if there are no clubcards in user's wallet" in new ClubcardServiceEnvironment {
    provideResponse(StatusCodes.NotFound)

    val ex = failingWith[NotFoundException](service.primaryClubcard()(validToken))
    assert(ex.error == None)
  }

  it should "add a new valid clubcard to user's wallet" in new ClubcardServiceEnvironment {
    provideJsonResponse(Created, s"""{"DisplayName":"test card","CardNumber":"$validCardNumber","IsPrimaryCard":true,"IsPrivilegeCard":false}""")

    whenReady(service.addClubcard(validCardNumber, "test card")(validToken)) { res =>
      assert(res == Clubcard(validCardNumber, "test card", isPrimaryCard = true, isPrivilegeCard = false))

      val requestCaptor = ArgumentCaptor.forClass(classOf[HttpRequest])
      verify(mockSendReceive).apply(requestCaptor.capture)
      assert(requestCaptor.getValue.entity.toOption.map(_.contentType) == Some(ContentTypes.`application/json`))
    }
  }

  // TODO: Midas returns 400 BadRequest at the moment
  ignore should "throw ConflictException when adding a valid clubcard that was registered before" in new ClubcardServiceEnvironment {
    provideJsonResponse(BadRequest, """{"Message":"Clubcard already registered with service"}""")

    val ex = failingWith[ConflictException](service.addClubcard(validCardNumber, "test card")(validToken))
    assert(ex.error == ErrorMessage("Clubcard already registered with service"))
  }

  it should "throw BadRequestException when adding invalid clubcard to user's wallet" in new ClubcardServiceEnvironment {
    provideJsonResponse(BadRequest, """{"Message":"The request is invalid."}""")

    val ex = failingWith[BadRequestException](service.addClubcard(validCardNumber, "test card")(validToken))
    assert(ex.error == ErrorMessage("The request is invalid."))
  }
}
