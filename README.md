# Follow Addon for Flashback

Follow Addon convierte una colocación libre de cámara en un tramo que sigue a un jugador dentro del editor de [Flashback](https://github.com/Moulberry/Flashback). La cámara conserva su relación espacial con el jugador mientras este se mueve. Si la posición relativa cambia en el punto final, Flashback interpola la distancia, la altura y los ángulos durante el tramo.

## Uso

1. Abre una repetición y coloca la cámara en el instante inicial.
2. Pulsa el nuevo icono de seguimiento situado a la derecha de los controles de reproducción del Timeline y elige un jugador.
3. Avanza al instante final y coloca la cámara como quieras que termine el plano.
4. Pulsa de nuevo el icono y selecciona **Marcar final**.

El tramo aparece como una pista nativa de seguimiento de entidad. Se puede editar, deshacer, guardar y exportar con las herramientas habituales de Flashback. Para continuar un seguimiento existente, el addon reutiliza su pista activa y añade los nuevos puntos.

## Compatibilidad

La versión actual se construye para Minecraft 26.2, Fabric Loader y Flashback 0.43.4 o posterior. El manifiesto admite 26.3, pero esa versión de Minecraft y su correspondiente Flashback deben publicarse antes de poder validar el binario. La integración específica con la interfaz de Flashback está concentrada en el mixin del Timeline para que una adaptación a 26.3 quede localizada si cambia su editor.

Flashback es una dependencia externa y no se incluye en el JAR del addon.

## Compilación

Usa `gradlew build`. El JAR instalable se genera en `build/libs`.
