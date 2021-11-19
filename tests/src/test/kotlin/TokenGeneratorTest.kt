import org.junit.jupiter.api.Assertions.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.api.TestInstance.Lifecycle;
import java.util.Date;
import java.io.File;
import com.jaredrummler.ktsh.Shell;
import kotlin.system.exitProcess;
import 	java.util.concurrent.TimeUnit;

/*TODO:
- Backup config files
    - clients (daps)
    - omejdn (daps)
    - scope_mappings (daps) 
- Configure config files
- Setup Keys
    - Containers
    - Server
- Setup Certs
    - Containers
    - Server
- Get dynmamic attributes from the config
    - clients.yml
    - omejdn.yml
- Initiate DAPS
- (Adapt) Execute tests
- Shut down DAPS
- Restore clients file
- Delete crypto material
 */

@TestInstance(Lifecycle.PER_CLASS)
class TokenGeneratorTest {
 
    private lateinit var generator: TokenGenerator
    private val keyPath : String = "../keys/test1.key";
    private val keyPathEC256 : String = "keys/client_ec256_info/ec256.pem";
    private val keyPathEC512 : String = "keys/client_ec512_info/ec512.pem";
    private val keyPath2 : String = "../keys/test2.key";
    // private var aud : String = "https://daps-dev.aisec.fraunhofer.de";
    //private var aud : String = "http://localhost:4567";
    private var aud : String = "";
    // private var aud2 : String = "https://w3id.org/idsa/code/IDS_CONNECTORS_ALL";
    //private var aud2 : String = "TestServer";
    private var aud2 : String = "";
    // private var iss2 : String = "https://daps.aisec.fraunhofer.de";
    //private var iss2 : String = "http://localhost:4567"; //  = aud
    private var iss2 : String = ""; //  = aud
    private var iss : String = "";
    //private var iss : String = "EE:93:13:B5:4F:A9:1B:F1:71:53:72:9D:7C:65:49:61:F2:6D:2C:12:"+
	//			      "keyid:EE:93:13:B5:4F:A9:1B:F1:71:53:72:9D:7C:65:49:61:F2:6D:2C:12";
    private var sub : String = "";
    //private var sub : String = iss;
    // TODO: iss and sub from 2nd client - index 18 from clients_lines
    
    private var clients_copy : String = "";
    private var omejdn_copy : String = "";
    private var scope_mapping_copy : String = "";

    private val shell : Shell = Shell("sh");

    @BeforeAll
    fun setupDAPS(){
        // Register two connectors for testing purposes
        var result = shell.run("cd .. && sh scripts/register_connector.sh test1 >> config/clients.yml");
        if (!result.isSuccess) {
            exitProcess(-1);
        }
        result = shell.run("sh scripts/register_connector.sh test2 >> config/clients.yml");
        if (!result.isSuccess) {
            exitProcess(-1);
        }

        // Backup certs from the server
        result = shell.run("mv omejdn-server/keys omejdn-server/keys-backup && mkdir omejdn-server/keys");
        if (!result.isSuccess) {
            exitProcess(-1);
        }

        // Create server's private key for testing
        result = shell.run("cd keys && openssl req -newkey rsa:2048 -new -batch -nodes -x509 -days 3650 -text -keyout daps.key -out daps.cert && cd ..");
        if (!result.isSuccess) {
            exitProcess(-1);
        }

        // Copy certs and server's private key into server config
        result = shell.run("cp keys/test*.cert omejdn-server/keys/");
        if (!result.isSuccess) {
            exitProcess(-1);
        }
        result = shell.run("cp keys/daps.key omejdn-server/keys/");
        if (!result.isSuccess) {
            exitProcess(-1);
        }

        // Setup DAPS configuration
        clients_copy = File("../omejdn-server/config/clients.yml").readText(Charsets.UTF_8);
        val clients_config = File("../config/clients.yml").readText(Charsets.UTF_8);
        File("../omejdn-server/config/clients.yml").writeText(clients_config);
        omejdn_copy = File("../omejdn-server/config/omejdn.yml").readText(Charsets.UTF_8);
        val omejdn_config : String = File("../config/omejdn.yml").readText(Charsets.UTF_8);
        File("../omejdn-server/config/omejdn.yml").writeText(omejdn_config);
        scope_mapping_copy = File("../omejdn-server/config/scope_mapping.yml").readText(Charsets.UTF_8);
        val scope_mapping_config : String = File("../config/scope_mapping.yml").readText(Charsets.UTF_8);
        File("../omejdn-server/config/scope_mapping.yml").writeText(scope_mapping_config);

        // Get attributes from the config files
        val clients_lines = clients_config.split("\n");
        iss = clients_lines[1].split("client_id: ")[1];
        sub = iss;
        val omejdn_lines = omejdn_config.split("\n");
        aud = omejdn_lines[2].split("host: ")[1];
        iss2 = aud;
        aud2 = omejdn_lines[3].split("accept_audience: ")[1];

        // TODO:  Initialize DAPS server
        /*result = shell.run("cd omejdn-server && ruby omejdn.rb") {
            // Kill the command after 1 minute
            timeout = Shell.Timeout(1, TimeUnit.MINUTES)
        }
        if (!result.isSuccess) {
            exitProcess(-1);
        }*/
    }

    @BeforeEach
    fun configureSystemUnderTest() {
        generator = TokenGenerator();
    }

    @AfterAll
    fun shutdownDAPS(){
        // TODO: Shutdown DAPS
        if(shell.isRunning()){
            shell.interrupt();
        }

        // Restore server's certs from backup
        var result = shell.run("cd .. && rm -r omejdn-server/keys/ && mv omejdn-server/keys-backup/ omejdn-server/keys/");
        if (!result.isSuccess) {
            exitProcess(-1);
        }

        // Restore existing DAPS configuration
        File("../config/clients.yml").writeText("---\n");
        File("../omejdn-server/config/clients.yml").writeText(clients_copy);
        File("../omejdn-server/config/omejdn.yml").writeText(omejdn_copy);
        File("../omejdn-server/config/scope_mapping.yml").writeText(scope_mapping_copy);

        // Cleanup testing keys directory
        result = shell.run("rm -r keys/*");
        if (!result.isSuccess) {
            exitProcess(-1);
        }

        // Shutdown shell
        shell.shutdown();
    }

    private fun print_test(text : String){
        println("\u001B[44m\u001B[30mTest Case: "+text+"\u001B[0m\n");
    }
    
    @Test
    @DisplayName("Requests and verifies an access_token")
    /* Base case, this is the norm and expected on how the server correctly gives
       back an access_token that the user can verify and utilize */
    fun getAccessTokenAndVerify() {
        print_test("Requests and verifies an access_token");
        //Variables to feed to the token
        val context : String = "https://w3id.org/idsa/contexts/context.jsonld";
        var type : String = "ids:DatRequestToken";
        val now : Long = Date().getTime() / 1000; // Divide by 1000 bc it is given in ms
        val iat : Long = now;
        val nbf : Long = now;
        val exp : Long = now + 3600;

        val response : String = generator.getToken(iss, aud, sub, context, type, iat, nbf, exp, keyPath, "RS256");
        assertTrue("access_token" in response);

        //Variables to verify the expected token
        type = "ids:DatPayload";
        val securityProfile : String = "idsc:TRUSTED_CONNECTOR_SECURITY_PROFILE";
        val referringConnector : String = "http://consumer-core-old.demo";
        val scope : String = "idsc:IDS_CONNECTOR_ATTRIBUTES_ALL";
        val transCert : String = "d2474d41b96b5f5ea499ce42176c2d4c466dbe5f60c1df07f646b576f5cbb04c";
        assertTrue(generator.verifyTokenRequest(response, iss2, aud2, sub, context, type,
                   securityProfile, referringConnector, scope, transCert));
    }

    @Test
    @DisplayName("Requests and verifies an access_token with the RSA512 algorithm")
    /* Base case, this is the norm and expected on how the server correctly gives
       back an access_token that the user can verify and utilize */
    fun getAccessTokenAndVerifyRSA512() {
        print_test("Requests and verifies an access_token with the RSA512 algorithm");
        //Variables to feed to the token
        val context : String = "https://w3id.org/idsa/contexts/context.jsonld";
        var type : String = "ids:DatRequestToken";
        val now : Long = Date().getTime() / 1000; // Divide by 1000 bc it is given in ms
        val iat : Long = now;
        val nbf : Long = now;
        val exp : Long = now + 3600;

        val response : String = generator.getToken(iss, aud, sub, context, type, iat, nbf, exp, keyPath, "RS512");
        assertTrue("access_token" in response);

        //Variables to verify the expected token
        type = "ids:DatPayload";
        val securityProfile : String = "idsc:TRUSTED_CONNECTOR_SECURITY_PROFILE";
        val referringConnector : String = "http://consumer-core-old.demo";
        val scope : String = "idsc:IDS_CONNECTOR_ATTRIBUTES_ALL";
        val transCert : String = "d2474d41b96b5f5ea499ce42176c2d4c466dbe5f60c1df07f646b576f5cbb04c";
        assertTrue(generator.verifyTokenRequest(response, iss2, aud2, sub, context, type,
                   securityProfile, referringConnector, scope, transCert));
    }

    @Test
    @DisplayName("Requests and verifies an access_token with the ES256 algorithm")
    /* Base case, this is the norm and expected on how the server correctly gives
       back an access_token that the user can verify and utilize */
    fun getAccessTokenAndVerifyES256() {
        print_test("Requests and verifies an access_token with the ES256 algorithm");
        //Variables to feed to the token
        val context : String = "https://w3id.org/idsa/contexts/context.jsonld";
        var type : String = "ids:DatRequestToken";
        val now : Long = Date().getTime() / 1000; // Divide by 1000 bc it is given in ms
        val iat : Long = now;
        val nbf : Long = now;
        val exp : Long = now + 3600;
        val iss : String =  "ec256";
        val sub : String =  iss;

        val response : String = generator.getToken(iss, aud, sub, context, type, iat, nbf, exp, keyPathEC256, "ES256");
        assertTrue("access_token" in response);

        //Variables to verify the expected token
        type = "ids:DatPayload";
        val securityProfile : String = "idsc:TRUSTED_CONNECTOR_SECURITY_PROFILE";
        val referringConnector : String = "http://consumer-core-old.demo";
        val scope : String = "idsc:IDS_CONNECTOR_ATTRIBUTES_ALL";
        val transCert : String = "d2474d41b96b5f5ea499ce42176c2d4c466dbe5f60c1df07f646b576f5cbb04c";
        assertTrue(generator.verifyTokenRequest(response, iss2, aud2, sub, context, type,
                   securityProfile, referringConnector, scope, transCert));
    }

    @Test
    @DisplayName("Requests and verifies an access_token with the ES512 algorithm")
    /* Base case, this is the norm and expected on how the server correctly gives
       back an access_token that the user can verify and utilize */
    fun getAccessTokenAndVerifyES512() {
        print_test("Requests and verifies an access_token with the ES512 algorithm");
        //Variables to feed to the token
        val context : String = "https://w3id.org/idsa/contexts/context.jsonld";
        var type : String = "ids:DatRequestToken";
        val now : Long = Date().getTime() / 1000; // Divide by 1000 bc it is given in ms
        val iat : Long = now;
        val nbf : Long = now;
        val exp : Long = now + 3600;
        val iss : String =  "ec512";
        val sub : String =  iss;

        val response : String = generator.getToken(iss, aud, sub, context, type, iat, nbf, exp, keyPathEC512, "ES512");
        assertTrue("access_token" in response);

        //Variables to verify the expected token
        type = "ids:DatPayload";
        val securityProfile : String = "idsc:TRUSTED_CONNECTOR_SECURITY_PROFILE";
        val referringConnector : String = "http://consumer-core-old.demo";
        val scope : String = "idsc:IDS_CONNECTOR_ATTRIBUTES_ALL";
        val transCert : String = "d2474d41b96b5f5ea499ce42176c2d4c466dbe5f60c1df07f646b576f5cbb04c";
        assertTrue(generator.verifyTokenRequest(response, iss2, aud2, sub, context, type,
                   securityProfile, referringConnector, scope, transCert));
    }

    @Test
    @DisplayName("Requests and verifies an access_token with the PS256 algorithm")
    /* This test fails because in the DAPS PS256 is not a supported algorithm */
    fun getAccessTokenAndVerifyPS256() {
        print_test("Requests and verifies an access_token with the PS256 algorithm");
        //Variables to feed to the token
        val context : String = "https://w3id.org/idsa/contexts/context.jsonld";
        var type : String = "ids:DatRequestToken";
        val now : Long = Date().getTime() / 1000; // Divide by 1000 bc it is given in ms
        val iat : Long = now;
        val nbf : Long = now;
        val exp : Long = now + 3600;

        val response : String = generator.getToken(iss, aud, sub, context, type, iat, nbf, exp, keyPath, "PS256");
        assertFalse("access_token" in response);
    }

    @Test
    @DisplayName("Requests and verifies an access_token with the PS512 algorithm")
    /* This test fails because in the DAPS PS512 is not a supported algorithm */
    fun getAccessTokenAndVerifyPS512() {
        print_test("Requests and verifies an access_token with the PS512 algorithm");
        //Variables to feed to the token
        val context : String = "https://w3id.org/idsa/contexts/context.jsonld";
        var type : String = "ids:DatRequestToken";
        val now : Long = Date().getTime() / 1000; // Divide by 1000 bc it is given in ms
        val iat : Long = now;
        val nbf : Long = now;
        val exp : Long = now + 3600;

        val response : String = generator.getToken(iss, aud, sub, context, type, iat, nbf, exp, keyPath, "PS512");
        assertFalse("access_token" in response);
    }

    @Test
    @DisplayName("NBF set to current time, IAT one hour before and EXP one hour in the future")
    /*  This test ssuccessfully creates an access_token. In theory this should not affect the security
        of the token because this behaviour can't really be abused. */
    fun futureNBFToken() {
        print_test("NBF set to one hour in the future after IAT but before EXP");
        //Variables to feed to the token
        val context : String = "https://w3id.org/idsa/contexts/context.jsonld";
        var type : String = "ids:DatRequestToken";
        val now : Long = Date().getTime() / 1000; // Divide by 1000 bc it is given in ms
        val iat : Long = now - 3600;
        val nbf : Long = now;
        val exp : Long = now + 3600;

        val response : String = generator.getToken(iss, aud, sub, context, type, iat, nbf, exp, keyPath, "RS256");
        assertTrue("access_token" in response);

        //Variables to verify the expected token
        type = "ids:DatPayload";
        val securityProfile : String = "idsc:TRUSTED_CONNECTOR_SECURITY_PROFILE";
        val referringConnector : String = "http://consumer-core-old.demo";
        val scope : String = "idsc:IDS_CONNECTOR_ATTRIBUTES_ALL";
        val transCert : String = "d2474d41b96b5f5ea499ce42176c2d4c466dbe5f60c1df07f646b576f5cbb04c";
        assertTrue(generator.verifyTokenRequest(response, iss2, aud2, sub, context, type,
                   securityProfile, referringConnector, scope, transCert));
    }

    @Test
    @DisplayName("IAT set to current time, NBF set one hour in the past, EXP one hour in the future")
    /* This test ssuccessfully creates an access_token. In theory this should not affect the security
       of the token because this behaviour can't really be abused. */
    fun pastNBFToken() {
        print_test("IAT set to current time, NBF set one hour in the past, EXP one hour in the future");
        //Variables to feed to the token
        val context : String = "https://w3id.org/idsa/contexts/context.jsonld";
        var type : String = "ids:DatRequestToken";
        val now : Long = Date().getTime() / 1000; // Divide by 1000 bc it is given in ms
        val iat : Long = now;
        val nbf : Long = now - 3600;
        val exp : Long = now + 3600;

        val response : String = generator.getToken(iss, aud, sub, context, type, iat, nbf, exp, keyPath, "RS256");
        assertTrue("access_token" in response);

        //Variables to verify the expected token
        type = "ids:DatPayload";
        val securityProfile : String = "idsc:TRUSTED_CONNECTOR_SECURITY_PROFILE";
        val referringConnector : String = "http://consumer-core-old.demo";
        val scope : String = "idsc:IDS_CONNECTOR_ATTRIBUTES_ALL";
        val transCert : String = "d2474d41b96b5f5ea499ce42176c2d4c466dbe5f60c1df07f646b576f5cbb04c";
        assertTrue(generator.verifyTokenRequest(response, iss2, aud2, sub, context, type,
                   securityProfile, referringConnector, scope, transCert));
    }

    @Test
    @DisplayName("Wrong context")
    /* This test should in theory fail because the given @context is wrong and does not match the
       specified context in the documentation, however it successfully creates an access_token. This implies
       that the context from the token requesting authorization is never checked. In theory, this
       should not be dangerous as the permission for access_token does not depend on the context
       property but it is a bug that could lead to confusion (and if it is not being checked and
       taken into account, what is even the purpose of it?) */
    fun wrongContext() {
        print_test("Wrong context");
        //Variables to feed to the token
        var context : String = "invalid_context";
        var type : String = "ids:DatRequestToken";
        val now : Long = Date().getTime() / 1000; // Divide by 1000 bc it is given in ms
        val iat : Long = now;
        val nbf : Long = now;
        val exp : Long = now + 3600;

        val response : String = generator.getToken(iss, aud, sub, context, type, iat, nbf, exp, keyPath, "RS256");
        assertTrue("access_token" in response);

        //Variables to verify the expected token
        type = "ids:DatPayload";
        context = "https://w3id.org/idsa/contexts/context.jsonld";
        val securityProfile : String = "idsc:TRUSTED_CONNECTOR_SECURITY_PROFILE";
        val referringConnector : String = "http://consumer-core-old.demo";
        val scope : String = "idsc:IDS_CONNECTOR_ATTRIBUTES_ALL";
        val transCert : String = "d2474d41b96b5f5ea499ce42176c2d4c466dbe5f60c1df07f646b576f5cbb04c";
        assertTrue(generator.verifyTokenRequest(response, iss2, aud2, sub, context, type,
                   securityProfile, referringConnector, scope, transCert));
    }

    @Test
    @DisplayName("Wrong type")
    /* This test should in theory fail because it contains an invalid @type according to the
       specification, however it successfully creates an access_token. In theory, this
       should not be dangerous as the permission for access_token does not depend on the type
       property but it is a bug that could lead to confusion (and if it is not being checked and
       taken into account, what is even the purpose of it?) */
    fun wrongType() {
        print_test("Wrong type");
        //Variables to feed to the token
        val context : String = "https://w3id.org/idsa/contexts/context.jsonld";
        var type : String = "invalid_type";
        val now : Long = Date().getTime() / 1000; // Divide by 1000 bc it is given in ms
        val iat : Long = now;
        val nbf : Long = now;
        val exp : Long = now + 3600;

        val response : String = generator.getToken(iss, aud, sub, context, type, iat, nbf, exp, keyPath, "RS256");
        assertTrue("access_token" in response);

        //Variables to verify the expected token
        type = "ids:DatPayload";
        val securityProfile : String = "idsc:TRUSTED_CONNECTOR_SECURITY_PROFILE";
        val referringConnector : String = "http://consumer-core-old.demo";
        val scope : String = "idsc:IDS_CONNECTOR_ATTRIBUTES_ALL";
        val transCert : String = "d2474d41b96b5f5ea499ce42176c2d4c466dbe5f60c1df07f646b576f5cbb04c";
        assertTrue(generator.verifyTokenRequest(response, iss2, aud2, sub, context, type,
                   securityProfile, referringConnector, scope, transCert));
    }

    @Test
    @DisplayName("Wrong subject")
    /* This test fails as expected. Would be a big vulnerability if a valid access_token
       would be granted for this JWT request token*/
    fun wrongSubject() {
        print_test("Wrong subject");
        //Variables to feed to the token
        val sub : String = "invalid_subject";
        val context : String = "https://w3id.org/idsa/contexts/context.jsonld";
        val type : String = "ids:DatRequestToken";
        val now : Long = Date().getTime() / 1000; // Divide by 1000 bc it is given in ms
        val iat : Long = now;
        val nbf : Long = now;
        val exp : Long = now + 3600;

        val response : String = generator.getToken(iss, aud, sub, context, type, iat, nbf, exp, keyPath, "RS256");
        assertFalse("access_token" in response);
    }

    @Test
    @DisplayName("Wrong audience")
    /* This test fails as expected. As seen in the discussion from the chatgroup this value
       is not really correctly implemented. In the client assertion JWT for the DAPS server,
       the audience should be the URL of the DAPS server, nevertheless, it currently has to be
       "http://localhost:4567" in order to successfully retrieve an access_token. Also the
       error given does not relate the client itself but the audience, so the error message is
       not completely correct. */
    fun wrongAudience() {
        print_test("Wrong audience");
        //Variables to feed to the token
        val aud : String = "invalid_audience";
        val context : String = "https://w3id.org/idsa/contexts/context.jsonld";
        val type : String = "ids:DatRequestToken";
        val now : Long = Date().getTime() / 1000; // Divide by 1000 bc it is given in ms
        val iat : Long = now;
        val nbf : Long = now;
        val exp : Long = now + 3600;

        val response : String = generator.getToken(iss, aud, sub, context, type, iat, nbf, exp, keyPath, "RS256");
        assertFalse("access_token" in response);
    }

    @Test
    @DisplayName("Wrong issuer")
    /* This test fails as expected. Would be a big vulnerability if a valid access_token
       would be granted for this JWT request token*/
    fun wrongIssuer() {
        print_test("Wrong issuer");
        //Variables to feed to the token
        val iss : String = "invalid_issuer";
        val context : String = "https://w3id.org/idsa/contexts/context.jsonld";
        val type : String = "ids:DatRequestToken";
        val now : Long = Date().getTime() / 1000; // Divide by 1000 bc it is given in ms
        val iat : Long = now;
        val nbf : Long = now;
        val exp : Long = now + 3600;

        val response : String = generator.getToken(iss, aud, sub, context, type, iat, nbf, exp, keyPath, "RS256");
        assertFalse("access_token" in response);
    }

    @Test
    @DisplayName("'alg = none' attack")
    /* https://www.chosenplaintext.ca/2015/03/31/jwt-algorithm-confusion.html This test fails as
       expected. The code in Client.extract_jwt_cid is responsible for this as it always checks
       for the algorithm with which the JWT has been signed and only allows the algorithms RS256,
       RS512, ES256a and ES512. This prevents the "alg = none" attack. Changing to an HMAC algorithm
       would also result in the same behaviour as HMAC algorithms are not acccepted by the DAPS. The
       error message in this test is misleading, as it says the client is not known but the problem
       is the signing algoritm is not accepted in the DAPS. */
    fun getAlgNone() {
        print_test("Attempts to get an access_token with no valid signature algorithm");
        //Variables to feed to the token
        val context : String = "https://w3id.org/idsa/contexts/context.jsonld";
        val type : String = "ids:DatRequestToken";
        val now : Long = Date().getTime() / 1000; // Divide by 1000 bc it is given in ms
        val iat : Long = now;
        val nbf : Long = now;
        val exp : Long = now + 3600;

        val response : String = generator.getTokenAlgNone(iss, aud, sub, context, type, iat, nbf, exp);
        assertFalse("access_token" in response);
    }

    @Test
    @DisplayName("JSON injection attack - expiration change")
    /* https://www.acunetix.com/blog/web-security-zone/what-are-json-injections/ Input seems to be sanitized.
       The only interesting field to influence is the "sub" field. All the other fields cannot influence the
       access_token that is created from the server because the DAPS server recalculates these fields or fills
       them in with different information. There is a check for the "iss" and "sub" to be equal in the server, thus
       in this case we set both to the same string that tries to perform the injection. %22,%22exp%22:1628853958
       is the appended string to the "sub", this attempts to create a new field with the following appended form: '"exp":1628853958'
       being 1628853958 an epoch time that we want to set for the exp in the access_token that we might receive.
       ERROR: Client EE:93:13:B5:4F:A9:1B:F1:71:53:72:9D:7C:65:49:61:F2:6D:2C:12:keyid:EE:93:13:B5:4F:A9:1B:F1:71:53:72:9D:7C:65:49:61:F2:6D:2C:12%22,%22exp%22:1628853958 does not exist
       is the ERROR displayed in the server, indicating that it could not find the client as it is correctly sanitized
       and there are no parsing vulnerabilities*/
    fun getJSONInjectionAttackExpiration() {
        print_test("Attempts to get an access_token with an specific expiration date"); //1628853958
        //Variables to feed to the token
        val context : String = "https://w3id.org/idsa/contexts/context.jsonld";
        var type : String = "ids:DatRequestToken";
        val now : Long = Date().getTime() / 1000; // Divide by 1000 bc it is given in ms
        val iat : Long = now;
        val nbf : Long = now;
        val exp : Long = now + 3600;

        val response : String = generator.getToken(iss, aud, sub, context, type, iat, nbf, exp, keyPath, "RS256");
        assertTrue("access_token" in response);

        //Variables to verify the expected token
        type = "ids:DatPayload";
        val securityProfile : String = "idsc:TRUSTED_CONNECTOR_SECURITY_PROFILE";
        val referringConnector : String = "http://consumer-core-old.demo";
        val scope : String = "idsc:IDS_CONNECTOR_ATTRIBUTES_ALL";
        val transCert : String = "d2474d41b96b5f5ea499ce42176c2d4c466dbe5f60c1df07f646b576f5cbb04c";
        assertTrue(generator.verifyTokenRequest(response, iss2, aud2, sub, context, type,
                   securityProfile, referringConnector, scope, transCert));
    }

    @Test
    @DisplayName("JSON injection attack - audience change")
    /*  %22,%22aud%22:%22desiredAudience%22 is the appended string to the "sub", this attempts to create a new field with the following appended form: '"aud":"desiredAudience"'
       being desiredAudience an audience that we might want access to but we might not be able to access it.
       ERROR: Client EE:93:13:B5:4F:A9:1B:F1:71:53:72:9D:7C:65:49:61:F2:6D:2C:12:keyid:EE:93:13:B5:4F:A9:1B:F1:71:53:72:9D:7C:65:49:61:F2:6D:2C:12%22,%22aud%22:%22desiredAudience%22 does not exist
       is the ERROR displayed in the server, indicating that it could not find the client as it is correctly sanitized
       and there are no parsing vulnerabilities. */
    fun getJSONInjectionAttackAudience() {
        print_test("Attempts to get an access_token with an specific user-controlled audience"); //1628853958
        //Variables to feed to the token
        val context : String = "https://w3id.org/idsa/contexts/context.jsonld";
        var type : String = "ids:DatRequestToken";
        val now : Long = Date().getTime() / 1000; // Divide by 1000 bc it is given in ms
        val iat : Long = now;
        val nbf : Long = now;
        val exp : Long = now + 3600;

        val response : String = generator.getToken(iss, aud, sub, context, type, iat, nbf, exp, keyPath, "RS256");
        assertTrue("access_token" in response);

        //Variables to verify the expected token
        type = "ids:DatPayload";
        val securityProfile : String = "idsc:TRUSTED_CONNECTOR_SECURITY_PROFILE";
        val referringConnector : String = "http://consumer-core-old.demo";
        val scope : String = "idsc:IDS_CONNECTOR_ATTRIBUTES_ALL";
        val transCert : String = "d2474d41b96b5f5ea499ce42176c2d4c466dbe5f60c1df07f646b576f5cbb04c";
        assertTrue(generator.verifyTokenRequest(response, iss2, aud2, sub, context, type,
                   securityProfile, referringConnector, scope, transCert));
    }

    @Test
    @DisplayName("Different subject for the first signing key")
    /* This test fails as expected because the key used to sign the JWT does not matche the signing key
       for the given subject in the JWT. The DAPS server chooses the key based on the subject of the
       requesting jwt. In this case, no (malicious) client can create JWTs for other subjects */
    fun getDiffSubKey1() {
        print_test("Attempts to get an access_token with another subject for the first client's signing key"); 
        //Variables to feed to the token
        var iss : String = "FC:63:8A:1D:CD:DE:A8:8C:CB:1E:5F:46:C3:5C:81:C3:78:6F:ED:C9:"+
				      "keyid:FC:63:8A:1D:CD:DE:A8:8C:CB:1E:5F:46:C3:5C:81:C3:78:6F:ED:C9";
        val sub : String = "FC:63:8A:1D:CD:DE:A8:8C:CB:1E:5F:46:C3:5C:81:C3:78:6F:ED:C9:"+
				      "keyid:FC:63:8A:1D:CD:DE:A8:8C:CB:1E:5F:46:C3:5C:81:C3:78:6F:ED:C9";
        val context : String = "https://w3id.org/idsa/contexts/context.jsonld";
        var type : String = "ids:DatRequestToken";
        val now : Long = Date().getTime() / 1000; // Divide by 1000 bc it is given in ms
        val iat : Long = now;
        val nbf : Long = now;
        val exp : Long = now + 3600;

        val response : String = generator.getToken(iss, aud, sub, context, type, iat, nbf, exp, keyPath, "RS256");
        assertFalse("access_token" in response);
    }

    @Test
    @DisplayName("Different subject for the second signing key")
    /* Check the description from the test before*/
    fun getDiffSubKey2() {
        print_test("Attempts to get an access_token with another subject for the second client's signing key"); 
        //Variables to feed to the token
        var iss : String = "EE:93:13:B5:4F:A9:1B:F1:71:53:72:9D:7C:65:49:61:F2:6D:2C:12:"+
                       "keyid:EE:93:13:B5:4F:A9:1B:F1:71:53:72:9D:7C:65:49:61:F2:6D:2C:12";
        val sub : String = "EE:93:13:B5:4F:A9:1B:F1:71:53:72:9D:7C:65:49:61:F2:6D:2C:12:"+
                       "keyid:EE:93:13:B5:4F:A9:1B:F1:71:53:72:9D:7C:65:49:61:F2:6D:2C:12";
        val context : String = "https://w3id.org/idsa/contexts/context.jsonld";
        var type : String = "ids:DatRequestToken";
        val now : Long = Date().getTime() / 1000; // Divide by 1000 bc it is given in ms
        val iat : Long = now;
        val nbf : Long = now;
        val exp : Long = now + 3600;

        val response : String = generator.getToken(iss, aud, sub, context, type, iat, nbf, exp, keyPath2, "RS256");
        assertFalse("access_token" in response);
    }

    @Test
    @DisplayName("NaN")
    /* Checks if NaN is accepted. It should cause an error when processing NaN */
    fun getNaN() {
        print_test("Requests and verifies an access_token but the algorithm creates a jwt with a NaN field");
        val response : String = generator.getTokenNaN();
        assertFalse("access_token" in response);
    }

    @Test
    @DisplayName("Nested JSON")
    /* Checks if nested JSON accepted. It should cause an error when processing the JSON because the max_nesting is 100 */
    fun getNested() {
        print_test("Requests and verifies an access_token but the algorithm creates a jwt with a deeply nested JSON");
        val response : String = generator.getTokenNested();
        assertFalse("access_token" in response);
    }

        @Test
    @DisplayName("MAC confusion attack")
    /* Checks if we can confuse the server by using an asymmetric public key as a symmetrical key for authentication */
    fun getConfusion() {
        print_test("Requests and verifies an access_token but the algorithm creates a jwt signed with a public asymmetrical aimed at confusing the server and creating a valid jwt.");
        //Variables to feed to the token
        val context : String = "https://w3id.org/idsa/contexts/context.jsonld";
        var type : String = "ids:DatRequestToken";
        val now : Long = Date().getTime() / 1000; // Divide by 1000 bc it is given in ms
        val iat : Long = now;
        val nbf : Long = now;
        val exp : Long = now + 3600;
        val response : String = generator.getTokenConfusion(iss, aud, sub, context, type, iat, nbf, exp);
        assertFalse("access_token" in response);
    }

    // client1
    // EE:93:13:B5:4F:A9:1B:F1:71:53:72:9D:7C:65:49:61:F2:6D:2C:12:
    // keyid:EE:93:13:B5:4F:A9:1B:F1:71:53:72:9D:7C:65:49:61:F2:6D:2C:12
    // client2
    // FC:63:8A:1D:CD:DE:A8:8C:CB:1E:5F:46:C3:5C:81:C3:78:6F:ED:C9:
    // keyid:FC:63:8A:1D:CD:DE:A8:8C:CB:1E:5F:46:C3:5C:81:C3:78:6F:ED:C9
    // go_client
    // 14:7F:AA:8D:65:9B:0B:25:6F:BC:A6:F5:FC:BC:3B:6D:3E:D5:A1:E3:
    // keyid:EE:93:13:B5:4F:A9:1B:F1:71:53:72:9D:7C:65:49:61:F2:6D:2C:12
}