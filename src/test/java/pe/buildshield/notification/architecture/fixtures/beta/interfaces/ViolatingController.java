package pe.buildshield.notification.architecture.fixtures.beta.interfaces;

import pe.buildshield.notification.architecture.fixtures.alpha.infrastructure.AlphaRepository;

class ViolatingController {

    private final AlphaRepository repo = new AlphaRepository();

    public String handle() {
        return repo.find();
    }
}
