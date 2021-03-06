package org.molgenis.data;

import static com.google.common.collect.Streams.stream;
import static java.util.Objects.requireNonNull;
import static org.molgenis.data.util.EntityTypeUtils.isSingleReferenceType;

import com.google.common.collect.Iterators;
import java.util.stream.Stream;
import org.molgenis.data.meta.model.Attribute;

public class CascadeDeleteRepositoryDecorator extends AbstractRepositoryDecorator<Entity> {
  private static final int BATCH_SIZE = 1000;

  private final DataService dataService;

  public CascadeDeleteRepositoryDecorator(
      Repository<Entity> delegateRepository, DataService dataService) {
    super(delegateRepository);
    this.dataService = requireNonNull(dataService);
  }

  @Override
  public void delete(Entity entity) {
    if (hasCascadeDeleteAttributes()) {
      prepareCascadeDeletes(entity);
      super.delete(entity);
      handleCascadeDeletes(entity);
    } else {
      delegate().delete(entity);
    }
  }

  @Override
  public void deleteById(Object id) {
    if (hasCascadeDeleteAttributes()) {
      Entity entity = findOneById(id);
      super.deleteById(id);
      handleCascadeDeletes(entity);
    } else {
      delegate().deleteById(id);
    }
  }

  @Override
  public void deleteAll() {
    if (hasCascadeDeleteAttributes()) {
      // finding during deletion is tricky business: can't use iterator or findAll here
      delegate()
          .forEachBatched(
              entitiesBatch -> {
                delegate().delete(entitiesBatch.stream());
                entitiesBatch.forEach(this::handleCascadeDeletes);
              },
              BATCH_SIZE);
    } else {
      delegate().deleteAll();
    }
  }

  @Override
  public void delete(Stream<Entity> entities) {
    if (hasCascadeDeleteAttributes()) {
      Iterators.partition(entities.iterator(), BATCH_SIZE)
          .forEachRemaining(
              entitiesBatch -> {
                entitiesBatch.forEach(this::prepareCascadeDeletes);
                super.delete(entitiesBatch.stream());
                entitiesBatch.forEach(this::handleCascadeDeletes);
              });
    } else {
      delegate().delete(entities);
    }
  }

  @Override
  public void deleteAll(Stream<Object> ids) {
    if (hasCascadeDeleteAttributes()) {
      Iterators.partition(ids.iterator(), BATCH_SIZE)
          .forEachRemaining(
              idsBatch -> {
                Stream<Entity> entities = findAll(idsBatch.stream());
                super.deleteAll(idsBatch.stream());
                entities.forEach(this::handleCascadeDeletes);
              });
    } else {
      delegate().deleteAll(ids);
    }
  }

  /**
   * Guarantee that referenced entities for attributes with cascade delete are loaded, e.g. in case
   * entity is a lazy or partial entity.
   */
  private void prepareCascadeDeletes(Entity entity) {
    getCascadeDeleteAttributes().forEach(attr -> entity.get(attr.getName()));
  }

  private void handleCascadeDeletes(Entity entity) {
    getCascadeDeleteAttributes().forEach(attr -> handleCascadeDeletes(entity, attr));
  }

  private void handleCascadeDeletes(Entity entity, Attribute attribute) {
    Stream<Entity> refEntityStream;
    if (isSingleReferenceType(attribute)) {
      Entity refEntity = entity.getEntity(attribute.getName());
      refEntityStream = refEntity != null ? Stream.of(refEntity) : Stream.empty();
    } else {
      Iterable<Entity> entities = entity.getEntities(attribute.getName());
      refEntityStream = stream(entities);
    }

    // delete one-by-one and first check if exists because entities might not exist due to earlier
    // deletes
    String refEntityTypeName = attribute.getRefEntity().getId();
    refEntityStream.forEach(
        refEntity -> {
          if (dataService.findOneById(refEntityTypeName, refEntity.getIdValue()) != null) {
            dataService.delete(refEntityTypeName, refEntity);
          }
        });
  }

  private Stream<Attribute> getCascadeDeleteAttributes() {
    return stream(getEntityType().getAtomicAttributes())
        .filter(attribute -> attribute.getCascadeDelete() != null && attribute.getCascadeDelete());
  }

  private boolean hasCascadeDeleteAttributes() {
    return stream(getEntityType().getAtomicAttributes())
        .anyMatch(
            attribute -> attribute.getCascadeDelete() != null && attribute.getCascadeDelete());
  }
}
