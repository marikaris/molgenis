package org.molgenis.auth;

import org.molgenis.data.support.DefaultEntityMetaData;
import org.springframework.stereotype.Component;

import static org.molgenis.MolgenisFieldTypes.MREF;
import static org.molgenis.MolgenisFieldTypes.STRING;
import static org.molgenis.MolgenisFieldTypes.XREF;

@Component
public class AuthorityMetaData extends DefaultEntityMetaData {

    public AuthorityMetaData() {
        super("authority");
        setAbstract(true);
        addAttribute(Authority.ROLE).setLabel("role").setNillable(true);
    }
}
