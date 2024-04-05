package dk.dbc.rawrepo;

import dk.dbc.invariant.InvariantUtil;
import dk.dbc.oss.ns.catalogingupdate.Authentication;
import dk.dbc.oss.ns.catalogingupdate.BibliographicRecord;
import dk.dbc.oss.ns.catalogingupdate.CatalogingUpdatePortType;
import dk.dbc.oss.ns.catalogingupdate.Options;
import dk.dbc.oss.ns.catalogingupdate.UpdateOptionEnum;
import dk.dbc.oss.ns.catalogingupdate.UpdateRecordRequest;
import dk.dbc.oss.ns.catalogingupdate.UpdateRecordResult;
import dk.dbc.oss.ns.catalogingupdate.UpdateService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import jakarta.xml.ws.BindingProvider;

/**
 * Update web service connector.
 * Instances of this class are NOT thread safe.
 */
public class UpdateServiceConnector {
    private static final Logger LOGGER = LoggerFactory.getLogger(UpdateServiceConnector.class);

    private static final String CONNECT_TIMEOUT_PROPERTY = "com.sun.xml.ws.connect.timeout";
    private static final String REQUEST_TIMEOUT_PROPERTY = "com.sun.xml.ws.request.timeout";
    private static final int CONNECT_TIMEOUT_DEFAULT_IN_MS = 60 * 1000;       // 1 minute
    private static final int REQUEST_TIMEOUT_DEFAULT_IN_MS = 60 * 60 * 1000;  // 60 minutes -- we wait and wait on open update.

    private final String endpoint;
    private final String userName;
    private final String password;

    /* web-service proxy */
    private final CatalogingUpdatePortType proxy;

    private final boolean validateOnly;

    /**
     * Class constructor
     *
     * @param endpoint web service endpoint base URL on the form "http(s)://host:port/path"
     * @param userName for authenticating any user requiring access to the webservice
     * @param password for authenticating any user requiring access to the webservice
     * @throws NullPointerException     if passed any null valued argument
     * @throws IllegalArgumentException if passed empty valued {@code endpoint}
     */
    public UpdateServiceConnector(String endpoint, String userName, String password, boolean validateOnly)
            throws NullPointerException, IllegalArgumentException {
        this(new UpdateService(), endpoint, userName, password, validateOnly);
    }

    /**
     * Class constructor
     *
     * @param service  web service client view of the CatalogingUpdate Web service
     * @param endpoint web service endpoint base URL on the form "http(s)://host:port/path"
     * @param userName for authenticating any user requiring access to the webservice
     * @param password for authenticating any user requiring access to the webservice
     * @throws NullPointerException     if passed any null valued argument
     * @throws IllegalArgumentException if passed empty valued {@code endpoint}, {@code userName}, {@code password}
     */
    UpdateServiceConnector(UpdateService service, String endpoint, String userName, String password, boolean validateOnly)
            throws NullPointerException, IllegalArgumentException {
        InvariantUtil.checkNotNullOrThrow(service, "service");
        this.endpoint = InvariantUtil.checkNotNullNotEmptyOrThrow(endpoint, "endpoint");
        this.userName = InvariantUtil.checkNotNullOrThrow(userName, "userName");
        this.password = InvariantUtil.checkNotNullOrThrow(password, "password");
        this.validateOnly = validateOnly;
        proxy = this.getProxy(service);
    }

    /**
     * Calls updateRecord operation of the Open Update Web service
     *
     * @param groupId             group id used for authorization
     * @param schemaName          the template towards which the validation should be performed
     * @param bibliographicRecord containing the MarcXChange to validate
     * @param trackingId          unique ID for each OpenUpdate request
     * @return UpdateRecordRequest instance
     * @throws NullPointerException     if passed any null valued {@code template} or {@code bibliographicRecord} argument
     * @throws IllegalArgumentException if passed empty valued {@code template}
     */
    public UpdateRecordResult updateRecord(String groupId, String schemaName, BibliographicRecord bibliographicRecord, String trackingId)
            throws NullPointerException, IllegalArgumentException {
        InvariantUtil.checkNotNullNotEmptyOrThrow(groupId, "groupId");
        InvariantUtil.checkNotNullNotEmptyOrThrow(schemaName, "schemaName");
        InvariantUtil.checkNotNullOrThrow(bibliographicRecord, "bibliographicRecord");
        LOGGER.trace("Using endpoint: {}", endpoint);
        final UpdateRecordRequest updateRecordRequest = buildUpdateRecordRequest(groupId, schemaName, bibliographicRecord, trackingId);
        return proxy.updateRecord(updateRecordRequest);
    }

    /**
     * Builds an UpdateRecordRequest
     *
     * @param groupId             group id used for authorization
     * @param schemaName          the template towards which the validation should be performed
     * @param bibliographicRecord containing the MarcXChange to validate
     * @param trackingId          unique ID for each OpenUpdate request
     * @return a new updateRecordRequest containing schemeName and bibliographicRecord
     */
    private UpdateRecordRequest buildUpdateRecordRequest(String groupId, String schemaName, BibliographicRecord bibliographicRecord, String trackingId) {
        UpdateRecordRequest updateRecordRequest = new UpdateRecordRequest();
        Authentication authentication = new Authentication();
        authentication.setGroupIdAut(groupId);
        authentication.setUserIdAut(userName);
        authentication.setPasswordAut(password);
        updateRecordRequest.setAuthentication(authentication);
        updateRecordRequest.setSchemaName(schemaName);
        updateRecordRequest.setBibliographicRecord(bibliographicRecord);
        updateRecordRequest.setTrackingId(trackingId);
        if (validateOnly) {
            if (updateRecordRequest.getOptions() == null) {
                Options options = new Options();
                options.getOption().add(UpdateOptionEnum.VALIDATE_ONLY);
                updateRecordRequest.setOptions(options);
            } else {
                updateRecordRequest.getOptions().getOption().add(
                        UpdateOptionEnum.VALIDATE_ONLY);
            }
        }
        return updateRecordRequest;
    }

    private CatalogingUpdatePortType getProxy(UpdateService service) {
        final CatalogingUpdatePortType proxy = service.getCatalogingUpdatePort();

        // We don't want to rely on the endpoint from the WSDL
        BindingProvider bindingProvider = (BindingProvider) proxy;
        bindingProvider.getRequestContext().put(BindingProvider.ENDPOINT_ADDRESS_PROPERTY, endpoint);
        // FixMe: timeouts should be made configurable
        bindingProvider.getRequestContext().put(CONNECT_TIMEOUT_PROPERTY, CONNECT_TIMEOUT_DEFAULT_IN_MS);
        bindingProvider.getRequestContext().put(REQUEST_TIMEOUT_PROPERTY, REQUEST_TIMEOUT_DEFAULT_IN_MS);

        return proxy;
    }
}
