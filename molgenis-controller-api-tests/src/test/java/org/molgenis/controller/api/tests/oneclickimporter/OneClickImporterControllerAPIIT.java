package org.molgenis.controller.api.tests.oneclickimporter;

import com.google.common.base.Strings;
import com.google.gson.Gson;
import com.google.gson.JsonArray;
import io.restassured.internal.ValidatableResponseImpl;
import io.restassured.response.ValidatableResponse;
import org.molgenis.controller.api.tests.rest.v2.RestControllerV2APIIT;
import org.molgenis.oneclickimporter.controller.OneClickImporterController;
import org.slf4j.Logger;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.Test;

import java.io.File;
import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static com.google.common.io.Resources.getResource;
import static io.restassured.RestAssured.baseURI;
import static io.restassured.RestAssured.given;
import static java.lang.Thread.sleep;
import static org.hamcrest.Matchers.equalTo;
import static org.molgenis.controller.api.tests.utils.RestTestUtils.*;
import static org.molgenis.controller.api.tests.utils.RestTestUtils.Permission.*;
import static org.slf4j.LoggerFactory.getLogger;
import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertTrue;

public class OneClickImporterControllerAPIIT
{
	private static final Logger LOG = getLogger(OneClickImporterControllerAPIIT.class);

	private static final String REST_TEST_USER = "api_v2_test_user";
	private static final String REST_TEST_USER_PASSWORD = "api_v2_test_user_password";
	private static final String V2_TEST_FILE = "/RestControllerV2_API_TestEMX.xlsx";
	private static final String V2_DELETE_TEST_FILE = "/RestControllerV2_DeleteEMX.xlsx";
	private static final String V2_COPY_TEST_FILE = "/RestControllerV2_CopyEMX.xlsx";
	private static final String API_V2 = "api/v2/";

	private static final String ONE_CLICK_IMPORT_EXCEL_FILE = "/OneClickImport_complex-valid.xlsx";
	private static final String ONE_CLICK_IMPORT_CSV_FILE = "/OneClickImport_complex-valid.csv";

	private String testUserToken;
	private String adminToken;
	private String testUserId;

	// Fields to store created entity ids from import test, used during cleanup to remove the entities
	private List<String> importedEntities = new ArrayList<>();
	private List<String> importPackages = new ArrayList<>();
	private List<String> importJobIds = new ArrayList<>();

	@BeforeClass
	public void beforeClass()
	{
		LOG.info("Read environment variables");
		String envHost = System.getProperty("REST_TEST_HOST");
		baseURI = Strings.isNullOrEmpty(envHost) ? DEFAULT_HOST : envHost;
		LOG.info("baseURI: " + baseURI);

		String envAdminName = System.getProperty("REST_TEST_ADMIN_NAME");
		String adminUserName = Strings.isNullOrEmpty(envAdminName) ? DEFAULT_ADMIN_NAME : envAdminName;
		LOG.info("adminUserName: " + adminUserName);

		String envAdminPW = System.getProperty("REST_TEST_ADMIN_PW");
		String adminPassword = Strings.isNullOrEmpty(envHost) ? DEFAULT_ADMIN_PW : envAdminPW;
		LOG.info("adminPassword: " + adminPassword);

		adminToken = login(adminUserName, adminPassword);

		LOG.info("Importing Test data");
		uploadEMX(adminToken, V2_TEST_FILE);
		uploadEMX(adminToken, V2_DELETE_TEST_FILE);
		uploadEMX(adminToken, V2_COPY_TEST_FILE);
		LOG.info("Importing Done");

		createUser(adminToken, REST_TEST_USER, REST_TEST_USER_PASSWORD);

		testUserId = getUserId(adminToken, REST_TEST_USER);
		LOG.info("testUserId: " + testUserId);

		grantSystemRights(adminToken, testUserId, "sys_md_Package", WRITE);
		grantSystemRights(adminToken, testUserId, "sys_md_EntityType", WRITE);
		grantSystemRights(adminToken, testUserId, "sys_md_Attribute", WRITE);

		grantSystemRights(adminToken, testUserId, "sys_FileMeta", WRITE);
		grantSystemRights(adminToken, testUserId, "sys_sec_Owned", READ);
		grantSystemRights(adminToken, testUserId, "sys_L10nString", WRITE);

		grantRights(adminToken, testUserId, "V2_API_TypeTestAPIV2", WRITE);
		grantRights(adminToken, testUserId, "V2_API_TypeTestRefAPIV2", WRITE);
		grantRights(adminToken, testUserId, "V2_API_LocationAPIV2", WRITE);
		grantRights(adminToken, testUserId, "V2_API_PersonAPIV2", WRITE);

		grantRights(adminToken, testUserId, "base_v2APITest1", WRITEMETA);
		grantRights(adminToken, testUserId, "base_v2APITest2", WRITEMETA);

		grantRights(adminToken, testUserId, "base_APICopyTest", WRITEMETA);

		grantPluginRights(adminToken, testUserId, "one-click-importer");
		grantSystemRights(adminToken, testUserId, "sys_job_JobExecution", READ);
		grantSystemRights(adminToken, testUserId, "sys_job_OneClickImportJobExecution", READ);

		testUserToken = login(REST_TEST_USER, REST_TEST_USER_PASSWORD);
	}

	@Test
	public void testOneClickImportExcelFile() throws IOException, URISyntaxException
	{
		oneClickImportTest(ONE_CLICK_IMPORT_EXCEL_FILE);
	}

	@Test
	public void testOneClickImportCsvFile() throws IOException, URISyntaxException
	{
		oneClickImportTest(ONE_CLICK_IMPORT_CSV_FILE);
	}

	private void oneClickImportTest(String fileToImport) throws URISyntaxException
	{
		URL resourceUrl = getResource(RestControllerV2APIIT.class, fileToImport);
		File file = new File(new URI(resourceUrl.toString()).getPath());

		// Post the file to be imported
		ValidatableResponse response = given().log()
											  .all()
											  .header(X_MOLGENIS_TOKEN, testUserToken)
											  .multiPart(file)
											  .post(OneClickImporterController.URI + "/upload")
											  .then()
											  .log()
											  .all()
											  .statusCode(OKE);

		// Verify the post returns a job url
		String jobUrl = ((ValidatableResponseImpl) response).originalResponse().asString();
		assertTrue(jobUrl.startsWith("/api/v2/sys_job_OneClickImportJobExecution/"));

		String jobStatus = given().log()
								  .all()
								  .header(X_MOLGENIS_TOKEN, testUserToken)
								  .get(jobUrl)
								  .then()
								  .statusCode(OKE)
								  .extract()
								  .path("status");

		List<String> validJobStats = Arrays.asList("PENDING", "RUNNING", "SUCCESS");
		assertTrue(validJobStats.contains(jobStatus));

		// Poll job until it finishes
		int pollIndex = 0;
		int maxPolls = 10;
		long pollInterval = 500L;
		while ((!jobStatus.equals("SUCCESS")) && pollIndex < maxPolls)
		{
			LOG.info("Import job status : " + jobStatus);
			jobStatus = pollJobForStatus(jobUrl);
			waitForNMillis(pollInterval);
			pollIndex++;
		}
		LOG.info("Import job status : " + jobStatus);
		assertEquals(jobStatus, "SUCCESS");

		// Extract the id of the entity created by the import
		ValidatableResponse completedJobResponse = given().log()
														  .all()
														  .header(X_MOLGENIS_TOKEN, testUserToken)
														  .get(jobUrl)
														  .then()
														  .statusCode(OKE);

		JsonArray entityTypeId = new Gson().fromJson(
				completedJobResponse.extract().jsonPath().get("entityTypes").toString(), JsonArray.class);
		String entityId = entityTypeId.get(0).getAsJsonObject().get("id").getAsString();
		String packageName = completedJobResponse.extract().path("package");
		String jobId = completedJobResponse.extract().path("identifier");

		// Store to use during cleanup
		importedEntities.add(entityId);
		importPackages.add(packageName);
		importJobIds.add(jobId);

		// Get the entity value to check the import
		ValidatableResponse entityResponse = given().log()
													.all()
													.header(X_MOLGENIS_TOKEN, testUserToken)
													.get(API_V2 + entityId
															+ "?attrs=~id,first_name,last_name,full_name,UMCG_employee,Age")
													.then()
													.log()
													.all();
		entityResponse.statusCode(OKE);

		// Check first row for expected values
		entityResponse.body("items[0].first_name", equalTo("Mark"));
		entityResponse.body("items[0].last_name", equalTo("de Haan"));
		entityResponse.body("items[0].full_name", equalTo("Mark de Haan"));
		entityResponse.body("items[0].UMCG_employee", equalTo(true));
		entityResponse.body("items[0].Age", equalTo(26));

		// Check last row for expected values
		entityResponse.body("items[9].first_name", equalTo("Jan"));
		entityResponse.body("items[9].UMCG_employee", equalTo(false));
		entityResponse.body("items[9].Age", equalTo(32));
	}

	private String pollJobForStatus(String jobUrl)
	{
		return given().header(X_MOLGENIS_TOKEN, testUserToken).get(jobUrl).then().extract().path("status");
	}

	private void waitForNMillis(Long numberOfMillis)
	{
		try
		{
			sleep(numberOfMillis);
		}
		catch (InterruptedException e)
		{
			LOG.error(e.getMessage());
		}
	}
}
