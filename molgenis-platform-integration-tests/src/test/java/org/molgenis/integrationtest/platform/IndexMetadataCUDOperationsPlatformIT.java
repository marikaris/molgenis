package org.molgenis.integrationtest.platform;

import static com.google.common.collect.Lists.newArrayList;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.molgenis.data.meta.AttributeType.EMAIL;
import static org.molgenis.data.meta.AttributeType.STRING;
import static org.molgenis.data.meta.AttributeType.getValueString;
import static org.molgenis.data.meta.model.EntityTypeMetadata.ENTITY_TYPE_META_DATA;
import static org.molgenis.integrationtest.platform.PlatformIT.waitForWorkToBeFinished;
import static org.molgenis.security.core.runas.RunAsSystemAspect.runAsSystem;

import org.molgenis.data.DataService;
import org.molgenis.data.Entity;
import org.molgenis.data.EntityTestHarness;
import org.molgenis.data.Query;
import org.molgenis.data.UnknownEntityException;
import org.molgenis.data.UnknownEntityTypeException;
import org.molgenis.data.elasticsearch.ElasticsearchService;
import org.molgenis.data.index.SearchService;
import org.molgenis.data.index.job.IndexJobScheduler;
import org.molgenis.data.meta.AttributeType;
import org.molgenis.data.meta.MetaDataService;
import org.molgenis.data.meta.model.Attribute;
import org.molgenis.data.meta.model.AttributeFactory;
import org.molgenis.data.meta.model.AttributeMetadata;
import org.molgenis.data.meta.model.EntityType;
import org.molgenis.data.meta.model.EntityTypeMetadata;
import org.molgenis.data.support.QueryImpl;
import org.molgenis.security.core.runas.RunAsSystemAspect;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class IndexMetadataCUDOperationsPlatformIT {
  private static final Logger LOG =
      LoggerFactory.getLogger(IndexMetadataCUDOperationsPlatformIT.class);

  public static void testIndexCreateMetaData(
      SearchService searchService,
      EntityType entityTypeStatic,
      EntityType entityTypeDynamic,
      MetaDataService metaDataService) {
    Query<Entity> q1 = new QueryImpl<>();
    q1.eq(EntityTypeMetadata.ID, entityTypeStatic.getId())
        .and()
        .eq(EntityTypeMetadata.PACKAGE, entityTypeStatic.getPackage());
    assertEquals(
        1,
        searchService.count(
            metaDataService
                .getEntityType(ENTITY_TYPE_META_DATA)
                .orElseThrow(() -> new UnknownEntityTypeException(entityTypeStatic.getId())),
            q1));

    Query<Entity> q2 = new QueryImpl<>();
    q2.eq(EntityTypeMetadata.ID, entityTypeDynamic.getId())
        .and()
        .eq(EntityTypeMetadata.PACKAGE, entityTypeDynamic.getPackage());
    assertEquals(
        1,
        searchService.count(
            metaDataService
                .getEntityType(ENTITY_TYPE_META_DATA)
                .orElseThrow(() -> new UnknownEntityTypeException(entityTypeDynamic.getId())),
            q2));
  }

  /** Test delete only for dynamic entity metadata static entity metadata cannot be deleted */
  public static void testIndexDeleteMetaData(
      ElasticsearchService searchService,
      DataService dataService,
      EntityType entityTypeDynamic,
      MetaDataService metaDataService,
      IndexJobScheduler indexService) {

    // 1. Verify that sys_test_TypeTestDynamic exists in mapping
    Query<Entity> q = new QueryImpl<>();
    q.eq(EntityTypeMetadata.ID, entityTypeDynamic.getId())
        .and()
        .eq(EntityTypeMetadata.PACKAGE, entityTypeDynamic.getPackage());
    assertEquals(
        1,
        searchService.count(
            metaDataService
                .getEntityType(ENTITY_TYPE_META_DATA)
                .orElseThrow(() -> new UnknownEntityTypeException(entityTypeDynamic.getId())),
            q));

    // 2. Delete sys_test_TypeTestDynamic metadata and wait on index
    runAsSystem(() -> dataService.getMeta().deleteEntityType(entityTypeDynamic.getId()));
    PlatformIT.waitForIndexToBeStable(
        metaDataService
            .getEntityType(EntityTypeMetadata.ENTITY_TYPE_META_DATA)
            .orElseThrow(() -> new UnknownEntityTypeException(entityTypeDynamic.getId())),
        indexService,
        LOG);
    waitForWorkToBeFinished(indexService, LOG);

    // 3. Verify that mapping is removed
    assertFalse(searchService.hasIndex(entityTypeDynamic));

    // 4. Reset context
    RunAsSystemAspect.runAsSystem(() -> metaDataService.addEntityType(entityTypeDynamic));
    waitForWorkToBeFinished(indexService, LOG);
  }

  /** Test metadata Updating an attribute */
  public static void testIndexUpdateMetaDataUpdateAttribute(
      ElasticsearchService searchService,
      EntityType entityTypeDynamic,
      MetaDataService metaDataService,
      IndexJobScheduler indexService) {
    // 1. Verify that sys_test_TypeTestDynamic exists in mapping
    Query<Entity> q = new QueryImpl<>();
    q.eq(EntityTypeMetadata.ID, entityTypeDynamic.getId())
        .and()
        .eq(EntityTypeMetadata.PACKAGE, entityTypeDynamic.getPackage());
    assertEquals(
        1,
        searchService.count(
            metaDataService
                .getEntityType(ENTITY_TYPE_META_DATA)
                .orElseThrow(() -> new UnknownEntityTypeException(entityTypeDynamic.getId())),
            q));

    // 2. Change dataType value of ATTR_EMAIL
    Attribute toUpdateAttribute = entityTypeDynamic.getAttribute(EntityTestHarness.ATTR_EMAIL);
    toUpdateAttribute.setDataType(STRING);
    Object toUpdateAttributeId = toUpdateAttribute.getIdValue();

    // 3. Perform update
    runAsSystem(() -> metaDataService.updateEntityType(entityTypeDynamic));
    waitForWorkToBeFinished(indexService, LOG);
    assertTrue(searchService.hasIndex(entityTypeDynamic));

    // 4. Verify metadata changed
    Query<Entity> q2 = new QueryImpl<>();
    EntityType emdActual =
        metaDataService
            .getEntityType(AttributeMetadata.ATTRIBUTE_META_DATA)
            .orElseThrow(
                () ->
                    new UnknownEntityException(
                        EntityTypeMetadata.ENTITY_TYPE_META_DATA,
                        AttributeMetadata.ATTRIBUTE_META_DATA));
    q2.eq(AttributeMetadata.ID, toUpdateAttributeId);
    q2.and();
    q2.eq(AttributeMetadata.TYPE, getValueString(STRING));
    assertEquals(1, searchService.count(emdActual, q2));

    // 5. Reset context
    toUpdateAttribute.setDataType(EMAIL);
    runAsSystem(
        () -> {
          metaDataService.deleteEntityType(entityTypeDynamic.getId());
          metaDataService.addEntityType(entityTypeDynamic);
        });
    waitForWorkToBeFinished(indexService, LOG);
  }

  /** Test metadata removing an attribute */
  public static void testIndexUpdateMetaDataRemoveAttribute(
      EntityType emd,
      String attributeName,
      ElasticsearchService searchService,
      MetaDataService metaDataService,
      IndexJobScheduler indexService) {
    // 1. Verify that sys_test_TypeTestDynamic exists in mapping
    Query<Entity> q = new QueryImpl<>();
    q.eq(EntityTypeMetadata.ID, emd.getId()).and().eq(EntityTypeMetadata.PACKAGE, emd.getPackage());
    assertEquals(
        1,
        searchService.count(
            metaDataService
                .getEntityType(ENTITY_TYPE_META_DATA)
                .orElseThrow(() -> new UnknownEntityTypeException(ENTITY_TYPE_META_DATA)),
            q));

    // 2. Remove attribute
    Attribute toRemoveAttribute = emd.getAttribute(attributeName);
    emd.removeAttribute(toRemoveAttribute);

    // 3. Perform update
    runAsSystem(() -> metaDataService.updateEntityType(emd));
    waitForWorkToBeFinished(indexService, LOG);
    assertTrue(searchService.hasIndex(emd));

    // 4. Verify metadata changed
    Query<Entity> q2 = new QueryImpl<>();
    EntityType emdActual =
        metaDataService
            .getEntityType(AttributeMetadata.ATTRIBUTE_META_DATA)
            .orElseThrow(
                () ->
                    new UnknownEntityException(
                        EntityTypeMetadata.ENTITY_TYPE_META_DATA,
                        AttributeMetadata.ATTRIBUTE_META_DATA));
    q2.eq(AttributeMetadata.ID, toRemoveAttribute.getIdValue());
    assertEquals(0, searchService.count(emdActual, q2));

    // 5. Reset context
    emd.addAttribute(toRemoveAttribute);
    runAsSystem(() -> metaDataService.updateEntityType(emd));
    waitForWorkToBeFinished(indexService, LOG);
  }

  public static void testIndexUpdateMetaDataRemoveCompoundAttribute(
      EntityType entityType,
      AttributeFactory attributeFactory,
      ElasticsearchService searchService,
      MetaDataService metaDataService,
      IndexJobScheduler indexService) {
    // 1. Create new compound to test delete
    Attribute compound = attributeFactory.create();
    compound.setName("test_compound");
    compound.setDataType(AttributeType.COMPOUND);

    Attribute compoundChild = attributeFactory.create();
    compoundChild.setName("test_compound_child");
    compoundChild.setParent(compound);

    entityType.addAttributes(newArrayList(compound, compoundChild));
    runAsSystem(() -> metaDataService.updateEntityType(entityType));
    waitForWorkToBeFinished(indexService, LOG);
    assertTrue(searchService.hasIndex(entityType));

    // 2. Verify compound and child got added
    EntityType afterAddEntityType =
        metaDataService
            .getEntityType(entityType.getId())
            .orElseThrow(() -> new UnknownEntityTypeException(entityType.getId()));
    assertNotNull(afterAddEntityType.getAttribute("test_compound"));
    assertNotNull(afterAddEntityType.getAttribute("test_compound_child"));

    // 3. Delete compound
    afterAddEntityType.removeAttribute(compound);
    runAsSystem(() -> metaDataService.updateEntityType(afterAddEntityType));
    waitForWorkToBeFinished(indexService, LOG);
    assertTrue(searchService.hasIndex(afterAddEntityType));

    // 4. Verify that compound + child was removed
    EntityType afterRemoveEntityType =
        metaDataService
            .getEntityType(entityType.getId())
            .orElseThrow(
                () ->
                    new UnknownEntityException(
                        EntityTypeMetadata.ENTITY_TYPE_META_DATA, entityType.getId()));
    org.junit.jupiter.api.Assertions.assertNull(
        afterRemoveEntityType.getAttribute(compound.getName()));
    org.junit.jupiter.api.Assertions.assertNull(
        afterRemoveEntityType.getAttribute(compoundChild.getName()));

    EntityType attributeMetadata =
        metaDataService
            .getEntityType(AttributeMetadata.ATTRIBUTE_META_DATA)
            .orElseThrow(
                () ->
                    new UnknownEntityException(
                        EntityTypeMetadata.ENTITY_TYPE_META_DATA,
                        AttributeMetadata.ATTRIBUTE_META_DATA));

    Query<Entity> compoundQuery = new QueryImpl<>().eq(AttributeMetadata.ID, compound.getIdValue());
    Query<Entity> childQuery =
        new QueryImpl<>().eq(AttributeMetadata.ID, compoundChild.getIdValue());

    assertEquals(0, searchService.count(attributeMetadata, compoundQuery));
    assertEquals(0, searchService.count(attributeMetadata, childQuery));
  }
}
