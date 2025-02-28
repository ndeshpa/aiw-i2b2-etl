package edu.emory.cci.aiw.i2b2etl.dest.table;

/*
 * #%L
 * AIW i2b2 ETL
 * %%
 * Copyright (C) 2012 - 2015 Emory University
 * %%
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * 
 *      http://www.apache.org/licenses/LICENSE-2.0
 * 
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 * #L%
 */
import edu.emory.cci.aiw.i2b2etl.dest.config.Settings;
import edu.emory.cci.aiw.i2b2etl.dest.metadata.Concept;
import edu.emory.cci.aiw.i2b2etl.dest.metadata.conceptid.ConceptId;
import edu.emory.cci.aiw.i2b2etl.dest.metadata.DataType;
import edu.emory.cci.aiw.i2b2etl.dest.metadata.conceptid.InvalidConceptCodeException;
import edu.emory.cci.aiw.i2b2etl.dest.metadata.Metadata;
import edu.emory.cci.aiw.i2b2etl.dest.metadata.MetadataUtil;
import edu.emory.cci.aiw.i2b2etl.dest.metadata.conceptid.SimpleConceptId;
import java.sql.SQLException;
import java.util.Calendar;
import java.util.Date;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TimeZone;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.apache.commons.lang3.StringUtils;
import org.arp.javautil.sql.ConnectionSpec;
import org.protempa.proposition.Proposition;
import org.protempa.proposition.UniqueId;
import org.protempa.proposition.value.Value;

/**
 *
 * @author arpost
 */
public class ProviderDimensionFactory {

    private static final String PROVIDER_ID_PREFIX = MetadataUtil.DEFAULT_CONCEPT_ID_PREFIX_INTERNAL + "|Provider:";
    private static final String NOT_RECORDED_PROVIDER_ID = PROVIDER_ID_PREFIX + "NotRecorded";
    private static final Calendar CAL;
    private static final Date PROVIDER_NOT_RECORDED_DATE;
    static {
        CAL = Calendar.getInstance();
        CAL.clear();
        CAL.setTimeZone(TimeZone.getTimeZone("America/New_York"));
        /*
         * Need to update every time the no-provider record is changed.
         */
        CAL.set(2016, Calendar.FEBRUARY, 24, 0, 0, 0);
        PROVIDER_NOT_RECORDED_DATE = CAL.getTime();
    }

    private final Metadata metadata;
    private final ProviderDimensionHandler providerDimensionHandler;

    public ProviderDimensionFactory(Metadata metadata, Settings settings, ConnectionSpec dataConnectionSpec) throws SQLException {
        this.metadata = metadata;
        this.providerDimensionHandler = new ProviderDimensionHandler(dataConnectionSpec);
    }

    private static enum DateType {
        CREATED,
        UPDATED,
        DELETED,
        DOWNLOADED
    }

    public ProviderDimension getInstance(Proposition encounterProp, String fullNameReference, String fullNameProperty,
            String firstNameReference, String firstNameProperty,
            String middleNameReference, String middleNameProperty,
            String lastNameReference, String lastNameProperty,
            Map<UniqueId, Proposition> references) throws InvalidConceptCodeException, SQLException {
        Set<String> sources = new HashSet<>(4);

        String firstName = extractNamePart(firstNameReference, firstNameProperty, encounterProp, references, sources);
        String middleName = extractNamePart(middleNameReference, middleNameProperty, encounterProp, references, sources);
        String lastName = extractNamePart(lastNameReference, lastNameProperty, encounterProp, references, sources);
        String fullName = extractNamePart(fullNameReference, fullNameProperty, encounterProp, references, sources);
        if (fullName == null) {
            fullName = constructFullName(firstName, middleName, lastName);
        }

        String id;
        String source;
        Date updated;
        Date downloaded;
        Date deleted;
        if (!sources.isEmpty()) {
            id = PROVIDER_ID_PREFIX + fullName;
            source = MetadataUtil.toSourceSystemCode(StringUtils.join(sources, " & "));
            updated = extract(DateType.UPDATED, fullNameReference, firstNameReference, middleNameReference, lastNameReference, encounterProp, references);
            if (updated == null) {
                updated = extract(DateType.CREATED, fullNameReference, firstNameReference, middleNameReference, lastNameReference, encounterProp, references);
            }
            downloaded = extract(DateType.DOWNLOADED, fullNameReference, firstNameReference, middleNameReference, lastNameReference, encounterProp, references);
            deleted = extract(DateType.DELETED, fullNameReference, firstNameReference, middleNameReference, lastNameReference, encounterProp, references);
        } else {
            id = NOT_RECORDED_PROVIDER_ID;
            source = this.metadata.getSourceSystemCode();
            fullName = "Not Recorded";
            updated = PROVIDER_NOT_RECORDED_DATE;
            downloaded = null;
            deleted = null;
        }
        ConceptId cid = SimpleConceptId.getInstance(id, this.metadata);
        Concept concept = this.metadata.getFromIdCache(cid);
        boolean found = concept != null;
        if (!found) {
            concept = new Concept(cid, null, this.metadata);
            concept.setSourceSystemCode(source);
            concept.setDisplayName(fullName);
            concept.setDataType(DataType.TEXT);
            concept.setInUse(true);
            concept.setFactTableColumn("provider_id");
            concept.setTableName("provider_dimension");
            concept.setColumnName("provider_path");
            this.metadata.addToIdCache(concept);
        }

        ProviderDimension providerDimension = new ProviderDimension();
        providerDimension.setConcept(concept);
        providerDimension.setSourceSystem(source);

        providerDimension.setUpdated(TableUtil.setTimestampAttribute(updated));
        providerDimension.setDownloaded(TableUtil.setTimestampAttribute(downloaded));
        providerDimension.setDeleted(TableUtil.setTimestampAttribute(deleted));
        if (!found) {
            this.metadata.addProvider(providerDimension);
            providerDimensionHandler.insert(providerDimension);
        }

        return providerDimension;
    }

    public void close() throws SQLException {
        this.providerDimensionHandler.close();
    }

    private Date extract(DateType dateType, String fullNameReference, String firstNameReference, String middleNameReference, String lastNameReference, Proposition encounterProp, Map<UniqueId, Proposition> references) {
        Date updated = null;
        if (fullNameReference != null) {
            updated = extract(dateType, fullNameReference, encounterProp, references);
        }
        if (firstNameReference != null) {
            Date u = extract(dateType, firstNameReference, encounterProp, references);
            if (u != null) {
                if (updated == null || u.after(updated)) {
                    updated = u;
                }
            }
        }
        if (middleNameReference != null) {
            Date u = extract(dateType, middleNameReference, encounterProp, references);
            if (u != null) {
                if (updated == null || u.after(updated)) {
                    updated = u;
                }
            }
        }
        if (lastNameReference != null) {
            Date u = extract(dateType, lastNameReference, encounterProp, references);
            if (u != null) {
                if (updated == null || u.after(updated)) {
                    updated = u;
                }
            }
        }
        return updated;
    }

    private Date extract(DateType dateType, String reference, Proposition encounterProp, Map<UniqueId, Proposition> references) {
        if (reference != null) {
            Proposition provider = resolveReference(encounterProp, reference, references);
            if (provider != null) {
                switch (dateType) {
                    case CREATED:
                        return provider.getCreateDate();
                    case UPDATED:
                        return provider.getUpdateDate();
                    case DELETED:
                        return provider.getDeleteDate();
                    case DOWNLOADED:
                        return provider.getDownloadDate();
                    default:
                        throw new AssertionError("unexpected dateType " + dateType);
                }
            } else {
                return null;
            }
        } else {
            return null;
        }
    }

    private String extractNamePart(String namePartReference, String namePartProperty, Proposition encounterProp, Map<UniqueId, Proposition> references, Set<String> sources) {
        if (namePartReference != null && namePartProperty != null) {
            Proposition provider = resolveReference(encounterProp, namePartReference, references);
            extractSource(sources, provider);
            return getNamePart(provider, namePartProperty);
        } else {
            return null;
        }
    }

    private void extractSource(Set<String> sources, Proposition provider) {
        if (provider != null) {
            sources.add(provider.getSourceSystem().getStringRepresentation());
        }
    }

    private Proposition resolveReference(Proposition encounterProp, String namePartReference, Map<UniqueId, Proposition> references) {
        Proposition provider;
        List<UniqueId> providerUIDs
                = encounterProp.getReferences(namePartReference);
        int size = providerUIDs.size();
        if (size > 0) {
            if (size > 1) {
                Logger logger = TableUtil.logger();
                logger.log(Level.WARNING,
                        "Multiple providers found for {0}, using only the first one",
                        encounterProp);
            }
            provider = references.get(providerUIDs.get(0));
        } else {
            provider = null;
        }
        return provider;
    }

    private String getNamePart(Proposition provider, String namePartProperty) {
        String namePart;
        if (provider != null) {
            namePart = getProperty(namePartProperty, provider);
        } else {
            namePart = null;
        }
        return namePart;
    }

    private String getProperty(String nameProperty, Proposition provider) {
        String name;
        if (nameProperty != null) {
            Value firstNameVal = provider.getProperty(nameProperty);
            if (firstNameVal != null) {
                name = firstNameVal.getFormatted();
            } else {
                name = null;
            }
        } else {
            name = null;
        }
        return name;
    }

    private String constructFullName(String firstName, String middleName, String lastName) {
        StringBuilder result = new StringBuilder();
        if (lastName != null) {
            result.append(lastName);
        }
        result.append(", ");
        if (firstName != null) {
            result.append(firstName);
        }
        if (middleName != null) {
            if (firstName != null) {
                result.append(' ');
            }
            result.append(middleName);
        }
        return result.toString();
    }
}
