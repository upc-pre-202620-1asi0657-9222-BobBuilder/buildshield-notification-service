# language: es
Característica: US43 Notificación de eventos
  Como responsable de una obra, un almacén o la empresa
  quiero enterarme el mismo día de los eventos que me tocan
  para actuar a tiempo sobre el material en camino y sus diferencias

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

  Escenario: El despacho en tránsito avisa al encargado de la obra destino
    Cuando "Rosa Quispe" despacha "MAN-0001" hacia la obra
    Entonces "Luis Ramos" tiene 1 notificación sin leer
    Y la última notificación de "Luis Ramos" dice "Despacho en camino a Torre Norte" y lleva a "/reception/{MAN-0001}"
    Y "Pedro Soto" tiene 0 notificaciones sin leer
    Y "Rosa Quispe" tiene 0 notificaciones sin leer

  Escenario: La incidencia en ruta avisa a la obra destino y a los administradores
    Cuando "Rosa Quispe" registra la incidencia "DELAY" con la observación "Tráfico en la Panamericana" en "MAN-0001"
    Entonces "Luis Ramos" tiene 1 notificación sin leer
    Y "Ana Torres" tiene 1 notificación sin leer
    Y la última notificación de "Ana Torres" dice "Incidencia en ruta: Retraso" y lleva a "/dispatches/{MAN-0001}"

  Escenario: La recepción con diferencias avisa al almacén de origen y a los administradores
    Cuando "Luis Ramos" confirma la recepción de "MAN-0001" con 1 material fuera de la tolerancia
    Entonces "Rosa Quispe" tiene 1 notificación sin leer
    Y "Ana Torres" tiene 1 notificación sin leer
    Y "Luis Ramos" tiene 0 notificaciones sin leer
    Y la última notificación de "Rosa Quispe" dice "Recepción con discrepancias" y lleva a "/discrepancies?dispatchId={MAN-0001}"

  Escenario: Los cambios de la discrepancia avisan a quien corresponde
    Cuando "Rosa Quispe" "responde" la discrepancia de "MAN-0001" abierta por "Luis Ramos" con el texto "Se repone mañana"
    Entonces la última notificación de "Luis Ramos" dice "Discrepancia respondida por el almacén" y lleva a "/discrepancies/{discrepancia MAN-0001}"
    Cuando "Luis Ramos" "escala" la discrepancia de "MAN-0001" abierta por "Luis Ramos" con el texto "No coincide"
    Entonces la última notificación de "Ana Torres" dice "Discrepancia escalada: requiere tu decisión" y lleva a "/discrepancies/{discrepancia MAN-0001}"
    Cuando "Ana Torres" "resuelve" la discrepancia de "MAN-0001" abierta por "Luis Ramos" con el texto "Se descuenta al transportista"
    Entonces la última notificación de "Rosa Quispe" dice "Discrepancia resuelta" y lleva a "/discrepancies/{discrepancia MAN-0001}"
    Y "Luis Ramos" tiene 2 notificaciones sin leer

  Escenario: Cada usuario marca como leídas solo sus notificaciones
    Cuando "Rosa Quispe" despacha "MAN-0001" hacia la obra
    Y "Rosa Quispe" registra la incidencia "DELAY" con la observación "Tráfico" en "MAN-0001"
    Y "Luis Ramos" marca como leída su última notificación
    Entonces "Luis Ramos" tiene 1 notificación sin leer
    Y la bandeja de "Luis Ramos" solo muestra no leídas: 1
    Y "Ana Torres" no puede marcar la última notificación de "Luis Ramos"
    Cuando "Luis Ramos" marca todas sus notificaciones como leídas
    Entonces "Luis Ramos" tiene 0 notificaciones sin leer
    Y "Ana Torres" tiene 1 notificación sin leer
