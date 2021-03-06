package org.molgenis.data.meta.model;

import static com.google.common.collect.Iterables.concat;
import static com.google.common.collect.Iterables.removeAll;
import static com.google.common.collect.Lists.newArrayList;
import static com.google.common.collect.Streams.stream;
import static java.util.Collections.singletonList;
import static java.util.stream.Collectors.toList;
import static org.molgenis.data.meta.model.PackageMetadata.CHILDREN;
import static org.molgenis.data.meta.model.PackageMetadata.ENTITY_TYPES;
import static org.molgenis.data.meta.model.PackageMetadata.ID;

import javax.annotation.CheckForNull;
import javax.annotation.Nullable;
import org.molgenis.data.Entity;
import org.molgenis.data.support.StaticEntity;

/**
 * Package defines the structure and attributes of a Package. Attributes are unique. Other software
 * components can use this to interact with Packages and/or to configure backends and frontends,
 * including Repository instances.
 */
@SuppressWarnings("SpringJavaAutowiringInspection")
public class Package extends StaticEntity {
  public static final String PACKAGE_SEPARATOR = "_";

  public Package(Entity entity) {
    super(entity);
  }

  /**
   * Constructs a package with the given meta data
   *
   * @param entityType package meta data
   */
  public Package(EntityType entityType) {
    super(entityType);
  }

  /**
   * Constructs a package with the given type code and meta data
   *
   * @param packageId package identifier (fully qualified package name)
   * @param entityType language meta data
   */
  public Package(String packageId, EntityType entityType) {
    super(entityType);
    setId(packageId);
  }

  /**
   * Copy-factory (instead of copy-constructor to avoid accidental method overloading to {@link
   * #Package(EntityType)})
   *
   * @param aPackage package
   * @return deep copy of package
   */
  public static Package newInstance(Package aPackage) {
    Package packageCopy = new Package(aPackage.getEntityType());
    packageCopy.setId(aPackage.getId());
    packageCopy.setLabel(aPackage.getLabel());
    packageCopy.setDescription(aPackage.getDescription());
    Package parent = aPackage.getParent();
    packageCopy.setParent(parent != null ? Package.newInstance(parent) : null);
    Iterable<Tag> tags = aPackage.getTags();
    packageCopy.setTags(stream(tags).map(Tag::newInstance).collect(toList()));
    return packageCopy;
  }

  public String getId() {
    return getString(ID);
  }

  public Package setId(String id) {
    set(ID, id);
    return this;
  }

  /**
   * Gets the parent package or null if this package does not have a parent package
   *
   * @return parent package or <tt>null</tt>
   */
  @Nullable
  @CheckForNull
  public Package getParent() {
    return getEntity(PackageMetadata.PARENT, Package.class);
  }

  public Package setParent(Package parentPackage) {
    set(PackageMetadata.PARENT, parentPackage);
    return this;
  }

  /**
   * Gets the subpackages of this package or an empty list if this package doesn't have any
   * subpackages.
   *
   * @return sub-packages
   */
  public Iterable<Package> getChildren() {
    return getEntities(CHILDREN, Package.class);
  }

  /**
   * The label of this package
   *
   * @return package label or <tt>null</tt>
   */
  public String getLabel() {
    return getString(PackageMetadata.LABEL);
  }

  public Package setLabel(String label) {
    set(PackageMetadata.LABEL, label);
    return this;
  }

  /**
   * The description of this package
   *
   * @return package description or <tt>null</tt>
   */
  @Nullable
  @CheckForNull
  public String getDescription() {
    return getString(PackageMetadata.DESCRIPTION);
  }

  public Package setDescription(String description) {
    set(PackageMetadata.DESCRIPTION, description);
    return this;
  }

  /**
   * Get all tags for this package
   *
   * @return package tags
   */
  public Iterable<Tag> getTags() {
    return getEntities(PackageMetadata.TAGS, Tag.class);
  }

  /**
   * Set tags for this package
   *
   * @param tags package tags
   * @return this package
   */
  public Package setTags(Iterable<Tag> tags) {
    set(PackageMetadata.TAGS, tags);
    return this;
  }

  /** Add a {@link Tag} to this {@link Package} */
  public void addTag(Tag tag) {
    set(PackageMetadata.TAGS, concat(getTags(), singletonList(tag)));
  }

  /** Remove a {@link Tag} from this {@link Package} */
  public void removeTag(Tag tag) {
    Iterable<Tag> tags = getTags();
    removeAll(tags, singletonList(tag));
    set(PackageMetadata.TAGS, tag);
  }

  /**
   * Gets the entities in this package. Does not return entities referred to by sub-packages
   *
   * @return package entities
   */
  public Iterable<EntityType> getEntityTypes() {
    return getEntities(ENTITY_TYPES, EntityType.class);
  }

  /**
   * Get the root of this package, or itself if this is a root package
   *
   * @return root package of this package or <tt>null</tt>
   */
  @SuppressWarnings("squid:S2259") // potential multi-threading NPE
  public Package getRootPackage() {
    Package aPackage = this;
    while (aPackage.getParent() != null) {
      aPackage = aPackage.getParent();
    }
    return aPackage;
  }

  /**
   * Based on generated AutoValue class:
   *
   * <pre><code>
   * {@literal @}AutoValue
   * public abstract class Package
   * {
   *     public abstract String getId();
   *     public abstract String getName();
   *    {@literal @}Nullable public abstract String getLabel();
   *    {@literal @}Nullable public abstract String getDescription();
   *    {@literal @}Nullable public abstract Package getParent();
   *     public abstract List<Tag> getTags();
   * }
   * </code></pre>
   */
  @SuppressWarnings("squid:S2259") // potential multi-threading NPEs
  @Override
  public boolean equals(Object o) {
    if (o == this) {
      return true;
    }
    if (o instanceof Package) {
      Package that = (Package) o;
      return (this.getId().equals(that.getId()))
          && ((this.getLabel() == null)
              ? (that.getLabel() == null)
              : this.getLabel().equals(that.getLabel()))
          && ((this.getDescription() == null)
              ? (that.getDescription() == null)
              : this.getDescription().equals(that.getDescription()))
          && ((this.getParent() == null)
              ? (that.getParent() == null)
              : this.getParent().equals(that.getParent()))
          && (newArrayList(this.getTags()).equals(newArrayList(that.getTags())));
    }
    return false;
  }

  /**
   * Based on generated AutoValue class:
   *
   * <pre><code>
   * {@literal @}AutoValue
   * public abstract class Package
   * {
   *     public abstract String getId();
   * }
   * </code></pre>
   */
  @Override
  public int hashCode() {
    int h = 1;
    h *= 1000003;
    h ^= this.getId().hashCode();
    return h;
  }

  @Override
  public String toString() {
    return "Package{" + "name=" + getId() + '}';
  }
}
