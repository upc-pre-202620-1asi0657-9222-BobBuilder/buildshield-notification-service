package pe.buildshield.notification.architecture.fixtures.beta.application;

import pe.buildshield.notification.architecture.fixtures.alpha.infrastructure.AlphaRepository;

/** Clase de ejemplo: un caso de uso que usa un adaptador de infraestructura (violación de capas). */
public class BetaService {

    private final AlphaRepository repository = new AlphaRepository();

    public String run() {
        return repository.find();
    }
}
