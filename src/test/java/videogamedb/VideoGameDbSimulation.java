package videogamedb;

import io.gatling.javaapi.core.*;
import io.gatling.javaapi.http.*;

import static io.gatling.javaapi.core.CoreDsl.*;
import static io.gatling.javaapi.http.HttpDsl.*;

public class VideoGameDbSimulation extends Simulation{
    //Http configuration
    private HttpProtocolBuilder httpProtocol = http
            .baseUrl("https://videogamedb.uk/api")
            .acceptHeader("application/json")
            .contentTypeHeader("application/json");

    //RUNTIME PARAMETERS
    private static final int USER_COUNT = Integer.parseInt(System.getProperty("USERS", "5"));
    private static final int RAMP_DURATION = Integer.parseInt(System.getProperty("RAMP_DURATION", "10"));

    //FEEDER FOR TEST DATA
    private static FeederBuilder.FileBased<Object> jsonFeeder = jsonFile("data/gameJsonFile.json").random();

    //BEFORE BLOCK
    @Override
    public void before(){
        System.out.printf("Running test with %d users%n", USER_COUNT);
        System.out.printf("Ramping users over %d seconds%n", RAMP_DURATION);
    }

    //Scenario Definition
    //get all video games, Authenticate, create a new game, get details of newly created game, delete newly created game
    //HTTP CALLS
    private static ChainBuilder getAllGames =
            exec(http("Get all games")
                    .get("/videogame"));

    private static ChainBuilder authenticate =
            exec(http("Authenticate")
                    .post("/authenticate")
                    .body(StringBody("{\n" +
                            " \"password\": \"admin\",\n" +
                            " \"username\": \"admin\"\n" +
                            "}"))
                    .check(jmesPath("token").saveAs("jwtToken"))
            );

    private static ChainBuilder createNewGame =
            feed(jsonFeeder)   // feeds json data from file
                    .exec(http("Create New Game - #{name}")  // this name word should match key in json file data
                    .post("/videogame")
                    .header("Authorization", "Bearer #{jwtToken}")
                    .body(ElFileBody("bodies/newGameTemplate.json")).asJson()
            );

    private static ChainBuilder getLastPostedGame =
            exec(http("Get Last Posted Game - #{name}")
                    .get("/videogame/#{id}")
                    .check(jmesPath("name").isEL("#{name}"))  // check to make sure we do not delete the game
            );

    private static ChainBuilder deleteLastPostedGame =
            exec(http("Delete Game - #{name}")
                    .delete("/videogame/#{id}")
                    .header("Authorization", "Bearer #{jwtToken}")
                    .check(bodyString().is("Video game deleted"))
            );
/*
    private static ChainBuilder deleteLastPostedGame =
            exec(http("Delete Game")
                    .delete("/videogame/1")
                    .header("Authorization", "Bearer #{jwtToken}")
            );

 */

    private ScenarioBuilder scn = scenario("Video Game DB Stress Test")
            .exec(getAllGames)
            .pause(2)  //pauses 2 seconds between each call
            .exec(authenticate)
            .pause(2)
            .exec(createNewGame)
            .pause(2)
            .exec(getLastPostedGame)
            .pause(2)
            .exec(deleteLastPostedGame);

    //Load Simulation with number of users specify  in atonceusers
    {
        setUp(
                scn.injectOpen(
                      nothingFor(5),     //do nothing for 5 seconds
                      rampUsers(USER_COUNT).during(RAMP_DURATION)
                )
        ).protocols(httpProtocol);
    }
}
