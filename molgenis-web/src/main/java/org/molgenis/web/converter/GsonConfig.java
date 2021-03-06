package org.molgenis.web.converter;

import com.baggonius.gson.immutable.ImmutableListDeserializer;
import com.baggonius.gson.immutable.ImmutableMapDeserializer;
import com.baggonius.gson.immutable.ImmutableSetDeserializer;
import com.baggonius.gson.optional.OptionalTypeFactory;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.google.gson.TypeAdapterFactory;
import com.google.gson.typeadapters.RuntimeTypeAdapterFactory;
import org.molgenis.data.Entity;
import org.molgenis.util.AutoGson;
import org.molgenis.web.menu.model.Menu;
import org.molgenis.web.menu.model.MenuItem;
import org.molgenis.web.menu.model.MenuNode;
import org.molgenis.web.support.EntitySerializer;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.converter.json.GsonHttpMessageConverter;

@Configuration
public class GsonConfig {
  @Value("${environment:production}")
  private String environment;

  @Bean
  public GsonHttpMessageConverter gsonHttpMessageConverter() {
    return new MolgenisGsonHttpMessageConverter(gsonFactoryBean().getObject());
  }

  @SuppressWarnings("unchecked")
  private <T> Class<? extends T> getAutoValueClass(Class<? extends T> clazz) {
    AutoGson annotation = clazz.getAnnotation(AutoGson.class);
    Class autoValueClass = annotation.autoValueClass();
    return (Class<? extends T>) autoValueClass;
  }

  @Bean
  public TypeAdapterFactory menuTypeAdapterFactory() {
    RuntimeTypeAdapterFactory<MenuNode> menuRuntimeTypeAdapterFactory =
        RuntimeTypeAdapterFactory.of(MenuNode.class, "type");
    menuRuntimeTypeAdapterFactory.registerSubtype(getAutoValueClass(MenuItem.class), "plugin");
    menuRuntimeTypeAdapterFactory.registerSubtype(getAutoValueClass(Menu.class), "menu");
    return menuRuntimeTypeAdapterFactory;
  }

  @SuppressWarnings("deprecation")
  @Bean
  public GsonFactoryBean gsonFactoryBean() {
    boolean prettyPrinting =
        environment != null && (environment.equals("development") || environment.equals("test"));

    GsonFactoryBean gsonFactoryBean = new GsonFactoryBean();
    gsonFactoryBean.registerTypeHierarchyAdapter(Entity.class, new EntitySerializer());
    gsonFactoryBean.registerTypeHierarchyAdapter(
        ImmutableList.class, new ImmutableListDeserializer());
    gsonFactoryBean.registerTypeHierarchyAdapter(
        ImmutableSet.class, new ImmutableSetDeserializer());
    gsonFactoryBean.registerTypeHierarchyAdapter(
        ImmutableMap.class, new ImmutableMapDeserializer());
    gsonFactoryBean.registerTypeAdapterFactory(OptionalTypeFactory.forJDK());
    gsonFactoryBean.setRegisterJavaTimeConverters(true);
    gsonFactoryBean.setDisableHtmlEscaping(true);
    gsonFactoryBean.setPrettyPrinting(prettyPrinting);
    gsonFactoryBean.setSerializeNulls(false);
    gsonFactoryBean.registerTypeAdapterFactory(menuTypeAdapterFactory());
    gsonFactoryBean.registerTypeAdapterFactory(new AutoValueTypeAdapterFactory());
    return gsonFactoryBean;
  }
}
