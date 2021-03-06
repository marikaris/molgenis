package org.molgenis.data;

import static java.util.Objects.requireNonNull;

import java.text.MessageFormat;
import javax.annotation.CheckForNull;
import javax.annotation.Nullable;
import org.molgenis.util.exception.ErrorCoded;
import org.molgenis.util.i18n.MessageSourceHolder;
import org.springframework.context.i18n.LocaleContextHolder;

/** {@link org.springframework.dao.DataAccessException} with error code and without message. */
@SuppressWarnings("squid:MaximumInheritanceDepth")
public abstract class ErrorCodedDataAccessException
    extends org.springframework.dao.DataAccessException implements ErrorCoded {
  private final String errorCode;

  public ErrorCodedDataAccessException(String errorCode) {
    this(errorCode, null);
  }

  public ErrorCodedDataAccessException(String errorCode, @Nullable @CheckForNull Throwable cause) {
    super("", cause);
    this.errorCode = requireNonNull(errorCode);
  }

  @Override
  public String getLocalizedMessage() {
    try {
      return MessageSourceHolder.getMessageSource()
          .getMessage(
              getErrorCode(),
              getLocalizedMessageArguments(),
              super.getLocalizedMessage(),
              LocaleContextHolder.getLocale());
    } catch (RuntimeException ex) {
      return MessageFormat.format(
          "FAILED TO FORMAT LOCALIZED MESSAGE FOR ERROR CODE {0}.%nFallback message: {1}",
          errorCode, super.getLocalizedMessage());
    }
  }

  protected abstract Object[] getLocalizedMessageArguments();

  @Override
  public String getErrorCode() {
    return errorCode;
  }
}
