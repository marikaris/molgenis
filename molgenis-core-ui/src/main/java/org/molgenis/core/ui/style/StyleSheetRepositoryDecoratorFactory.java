package org.molgenis.core.ui.style;

import static java.util.Objects.requireNonNull;

import org.molgenis.core.ui.settings.AppDbSettings;
import org.molgenis.data.AbstractSystemRepositoryDecoratorFactory;
import org.molgenis.data.Repository;
import org.springframework.stereotype.Component;

@Component
public class StyleSheetRepositoryDecoratorFactory
    extends AbstractSystemRepositoryDecoratorFactory<StyleSheet, StyleSheetMetadata> {
  private final AppDbSettings appDbSettings;

  public StyleSheetRepositoryDecoratorFactory(
      StyleSheetMetadata styleSheetMetadata, AppDbSettings appDbSettings) {
    super(styleSheetMetadata);
    this.appDbSettings = requireNonNull(appDbSettings);
  }

  @Override
  public Repository<StyleSheet> createDecoratedRepository(Repository<StyleSheet> repository) {
    return new StyleSheetRepositoryDecorator(repository, appDbSettings);
  }
}
