# language: es
Característica: US45 Configuración de destinatarios de notificación
  Como administrador de la empresa
  quiero elegir, por obra, quién recibe cada tipo de aviso
  para que la información llegue a quien debe actuar y no al resto del equipo

  Antecedentes:
    Dado que en la organización trabajan "Ana Torres" como "administrador"
    Y que en la organización trabajan "Rosa Quispe" como "encargado de almacén"
    Y que en la organización trabajan "Luis Ramos" como "encargado de obra"
    Y que en la organización trabajan "Pedro Soto" como "encargado de obra"
    Y que existen la obra "Torre Norte" y el almacén "Almacén Central"
    Y que "Luis Ramos" está asignado a "Torre Norte"
    Y que "Pedro Soto" está asignado a "Edificio Sur"
    Y que "Rosa Quispe" está asignado a "Almacén Central"
    Y que "Rosa Quispe" preparó el despacho "MAN-0001" del "Almacén Central" a "Torre Norte"

  Escenario: Sin configuración la obra usa la regla por defecto
    Cuando "Ana Torres" consulta los destinatarios de "Torre Norte"
    Entonces el aviso "DISPATCH_IN_TRANSIT" usa la regla por defecto "Encargados de obra asignados a la obra"
    Y puede elegir a "Ana Torres, Luis Ramos, Rosa Quispe"

  Escenario: El administrador elige quién recibe el despacho en camino
    Cuando "Ana Torres" elige a "Ana Torres" para el aviso "DISPATCH_IN_TRANSIT" de "Torre Norte"
    Entonces la respuesta tiene estado 200
    Cuando "Rosa Quispe" despacha "MAN-0001" hacia la obra
    Entonces "Ana Torres" tiene 1 notificación sin leer
    Y "Luis Ramos" tiene 0 notificaciones sin leer

  Escenario: Al volver a la regla por defecto avisa otra vez al encargado de la obra
    Cuando "Ana Torres" elige a "Ana Torres" para el aviso "DISPATCH_IN_TRANSIT" de "Torre Norte"
    Y "Ana Torres" vuelve a la regla por defecto del aviso "DISPATCH_IN_TRANSIT" de "Torre Norte"
    Entonces la respuesta tiene estado 204
    Cuando "Rosa Quispe" despacha "MAN-0001" hacia la obra
    Entonces "Luis Ramos" tiene 1 notificación sin leer
    Y "Ana Torres" tiene 0 notificaciones sin leer

  Escenario: Un encargado de otra obra no se puede elegir
    Cuando "Ana Torres" elige a "Pedro Soto" para el aviso "DISPATCH_IN_TRANSIT" de "Torre Norte"
    Entonces la respuesta tiene estado 400 con el código "RECIPIENT_NOT_ASSIGNED"

  Escenario: Solo el administrador configura los destinatarios
    Cuando "Rosa Quispe" elige a "Rosa Quispe" para el aviso "DISPATCH_IN_TRANSIT" de "Torre Norte"
    Entonces la respuesta tiene estado 403
