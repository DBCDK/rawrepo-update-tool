package dk.dbc.rawrepo;

import dk.dbc.marc.binding.DataField;
import dk.dbc.marc.binding.Field;
import dk.dbc.marc.binding.MarcRecord;
import dk.dbc.marc.binding.SubField;
import dk.dbc.marc.reader.MarcReader;
import dk.dbc.marc.reader.MarcReaderException;
import dk.dbc.marc.writer.MarcXchangeV1Writer;
import dk.dbc.rawrepo.bindings.BibliographicRecordExtraData;
import dk.dbc.rawrepo.bindings.BibliographicRecordExtraDataMarshaller;
import dk.dbc.updateservice.service.api.BibliographicRecord;
import dk.dbc.updateservice.service.api.DoubleRecordEntries;
import dk.dbc.updateservice.service.api.DoubleRecordEntry;
import dk.dbc.updateservice.service.api.ExtraRecordData;
import dk.dbc.updateservice.service.api.MessageEntry;
import dk.dbc.updateservice.service.api.Messages;
import dk.dbc.updateservice.service.api.RecordData;
import dk.dbc.updateservice.service.api.UpdateRecordResult;
import dk.dbc.updateservice.service.api.UpdateStatusEnum;
import org.w3c.dom.Document;
import org.xml.sax.SAXException;

import javax.xml.bind.JAXBException;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import javax.xml.ws.WebServiceException;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

class UpdateServiceHandler {
    private static final String RECORD_SCHEMA = "info:lc/xmlns/marcxchange-v1";
    private static final String RECORD_PACKAGING = "xml";
    private final BibliographicRecordExtraDataMarshaller bibliographicRecordExtraDataMarshaller =
            new BibliographicRecordExtraDataMarshaller();

    private int errorCount = 0;

    private final String username;
    private final String groupId;
    private final String password;
    private final String template;
    private final String trackingId;
    private final Integer priority;
    private final String provider;
    private final boolean validateOnly;
    private final int errorLimit;
    private final String updateServiceUrl;

    UpdateServiceHandler(String username,
                         String groupId,
                         String password,
                         String template,
                         String trackingId,
                         Integer priority,
                         String provider,
                         boolean validateOnly,
                         int errorLimit,
                         String updateServiceUrl) {
        this.username = username;
        this.groupId = groupId;
        this.password = password;
        this.template = template;
        this.trackingId = trackingId;
        this.priority = priority;
        this.provider = provider;
        this.validateOnly = validateOnly;
        this.errorLimit = errorLimit;
        this.updateServiceUrl = updateServiceUrl;
    }

    void run(MarcReader marcReader) {
        UpdateServiceConnector updateServiceConnector = new UpdateServiceConnector(updateServiceUrl, username, password, validateOnly);

        try {
            final DocumentBuilder dBuilder = DocumentBuilderFactory.newInstance().newDocumentBuilder();

            final int threadCount = 8;
            int loopCount = 0;

            ExecutorService executor = Executors.newFixedThreadPool(threadCount);
            MarcRecord record = marcReader.read();
            while (record != null) {
                loopCount++;

                if (errorLimit > -1 && errorCount > errorLimit) {
                    throw new RuntimeException("Hit error limit, so aborting");
                }

                final Runnable worker = new UpdateThead(updateServiceConnector, dBuilder, record);
                executor.execute(worker);

                record = marcReader.read();

                if (loopCount % 80 == 0 || record == null) {
                    executor.shutdown();
                    executor.awaitTermination(60, TimeUnit.MINUTES);
                    executor = Executors.newFixedThreadPool(threadCount);
                    System.out.println(loopCount);
                }
            }
        } catch (ParserConfigurationException e) {
            throw new RuntimeException();
        } catch (InterruptedException e) {
            System.out.println("Interrupt exception - aborting.");
            throw new RuntimeException();

        } catch (MarcReaderException e) {
            e.printStackTrace();
        }
    }

    private BibliographicRecord buildRecord(Document content) throws JAXBException {
        final BibliographicRecord bibliographicRecord = new BibliographicRecord();
        bibliographicRecord.setRecordSchema(RECORD_SCHEMA);
        bibliographicRecord.setRecordPacking(RECORD_PACKAGING);

        final ExtraRecordData extraRecordData = new ExtraRecordData();

        if (provider != null || priority != null) {
            final BibliographicRecordExtraData bibliographicRecordExtraData = new BibliographicRecordExtraData();

            if (priority != null) {
                bibliographicRecordExtraData.setPriority(priority);
            }

            if (provider != null) {
                bibliographicRecordExtraData.setProviderName(provider);
            }

            synchronized (bibliographicRecordExtraDataMarshaller) {
                extraRecordData.getContent().add(bibliographicRecordExtraDataMarshaller
                        .toXmlDocument(bibliographicRecordExtraData).getDocumentElement());
            }
        }
        bibliographicRecord.setExtraRecordData(extraRecordData);

        final RecordData recordData = new RecordData();
        recordData.getContent().add(content.getDocumentElement());

        bibliographicRecord.setRecordData(recordData);

        return bibliographicRecord;
    }

    private class UpdateThead implements Runnable {
        private final DocumentBuilder dBuilder;
        private final UpdateServiceConnector updateServiceConnector;
        private final MarcRecord marcRecord;

        UpdateThead(UpdateServiceConnector updateServiceConnector, DocumentBuilder dBuilder, MarcRecord marcRecord) {
            this.updateServiceConnector = updateServiceConnector;
            this.dBuilder = dBuilder;
            this.marcRecord = marcRecord;
        }

        @Override
        public void run() {
            try {
                try {
                    final Document doc;
                    final MarcXchangeV1Writer writer = new MarcXchangeV1Writer();
                    synchronized (dBuilder) {
                        doc = dBuilder.parse(new ByteArrayInputStream(writer.writeRecord(marcRecord, Charset.forName("UTF-8"))));
                    }

                    final BibliographicRecord bibliographicRecord = buildRecord(doc);
                    final UpdateRecordResult result = updateServiceConnector.updateRecord(groupId, template, bibliographicRecord, trackingId);
                    // Error handling. If a record fails in update service we need to know the id of the failed record.
                    if (result.getUpdateStatus() != UpdateStatusEnum.OK) {
                        errorCount++; // Check for error count last so that we get the last error message
                        final Optional<Field> field001 = marcRecord.getField(MarcRecord.hasTag("001"));
                        String recordId = "unknown", recordAgencyId = "unknown";
                        if (field001.isPresent()) {
                            final DataField dataField001 = (DataField) field001.get();
                            for (SubField subField : dataField001.getSubfields()) {
                                if (subField.getCode() == 'a') {
                                    recordId = subField.getData();
                                }

                                if (subField.getCode() == 'b') {
                                    recordAgencyId = subField.getData();
                                }
                            }
                        }

                        if (result.getMessages() != null) {
                            //System.out.println("Error updating '" + recordId + ":" + recordAgencyId + "'. Got message: " + result.getMessages());

                            final StringBuilder sb = new StringBuilder();
                            sb.append("Error updating '" + recordId + ":" + recordAgencyId + "'. ");
                            sb.append("Got message: ");

                            final List<String> messageList = new ArrayList<>();
                            final Messages messages = result.getMessages();
                            for (MessageEntry message : messages.getMessageEntry()) {
                                messageList.add(message.getMessage());
                            }
                            sb.append(String.join(", ", messageList));
                            System.out.println(sb.toString());

                        } else if (result.getDoubleRecordEntries() != null) {
                            final String doubleRecordKey = result.getDoubleRecordKey();
                            final List<String> messages = new ArrayList<>();

                            final DoubleRecordEntries doubleRecordEntries = result.getDoubleRecordEntries();
                            for (DoubleRecordEntry doubleRecordEntry : doubleRecordEntries.getDoubleRecordEntry()) {
                                if (!messages.contains(doubleRecordEntry.getMessage())) {
                                    messages.add(doubleRecordEntry.getMessage());
                                }
                            }
                            System.out.println("Error updating '" + recordId + ":" + recordAgencyId +
                                    "'. Got double record error with key: " + doubleRecordKey + " and message(s): " + String.join(", ", messages));
                        } else {
                            System.out.println("Error updating '" + recordId + ":" + recordAgencyId + "'. Got message: " + result);
                        }
                    }
                } catch (WebServiceException | NullPointerException | IllegalArgumentException ex) {
                    System.out.println("Caught exception from update: " + ex.toString());
                    errorCount++;
                    throw new RuntimeException("Aborting");
                } catch (SAXException ex) {
                    System.out.println("Could not parse line");
                    errorCount++;
                    throw new RuntimeException("Aborting");
                } catch (JAXBException e) {
                    System.out.println("Could not create extra record data.");
                    errorCount++;
                    throw new RuntimeException("Aborting");
                }
            } catch (IOException ex) {
                System.out.println("Could not read line");
                errorCount++;
                throw new RuntimeException("Aborting");
            }
        }
    }
}
