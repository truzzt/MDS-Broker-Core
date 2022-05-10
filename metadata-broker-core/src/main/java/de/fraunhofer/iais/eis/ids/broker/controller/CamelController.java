package de.fraunhofer.iais.eis.ids.broker.controller;

import de.fraunhofer.iais.eis.ids.component.core.SecurityTokenProvider;
import de.fraunhofer.iais.eis.ids.component.core.SelfDescriptionProvider;
import de.fraunhofer.iais.eis.ids.component.core.TokenRetrievalException;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;
import java.util.Collection;

@RestController
public class CamelController  {

    public static SecurityTokenProvider securityTokenProvider;
    public static SelfDescriptionProvider selfDescriptionProvider;
    public static Collection<String> trustedJwksHosts;

    @GetMapping("/token")
    public String fetchToken() throws TokenRetrievalException {
        return securityTokenProvider.getSecurityToken();
    }

    @GetMapping("/selfDescription")
    public String getSelfDescription() {
        return selfDescriptionProvider.getSelfDescription().toRdf();
    }

    @GetMapping("/getTrustedJwksHost")
    public String getTrustedHost() { return String.join(",", trustedJwksHosts); }

}