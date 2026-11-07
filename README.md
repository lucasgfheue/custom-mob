# Creature Studio — NeoForge 1.21.1

Crea **tus propias criaturas dentro de Minecraft**, sin programar: modélalas con cubos (como en
Blockbench), píntalas píxel a píxel, anima sus partes y define cómo se comportan (como en MCreator).
También puedes partir del modelo de cualquier mob de Minecraft y modificarlo.

## Características

La **vista 3D ocupa casi toda la pantalla** (como en AutoCAD). A la derecha hay un panel estrecho con una
fila de **pestañas con iconos** (pasa el ratón por encima para ver su nombre; teclas 1, 2, 3...):

- **H** oculta o muestra el panel (o el botón **»** a la izquierda de las pestañas) para dibujar con toda la pantalla.
- Arrastra el **borde izquierdo del panel** para hacerlo más ancho o más estrecho (se recuerda).
- La **rueda del ratón acerca y aleja hacia donde está el cursor**, no hacia el centro. **F** encuadra el modelo.

| Pestaña | Qué hay dentro |
|---|---|
| **Plantillas** | 9 plantillas del mod y **los modelos de todas las entidades de Minecraft** (con buscador). Pasa el ratón para verlos en 3D; con un clic se cargan con su forma, sus articulaciones y su textura original, listos para editar. |
| **Cuerpo** | **Partes del cuerpo ya hechas** (cuerpo de 2 o 4 patas, cabeza, ojos, orejas, cuernos, hocico, antenas, brazos, piernas, patas de araña, cola, alas y aletas): con un clic aparecen colocadas, unidas y animadas. Después, botones para hacerlas más grandes o pequeñas, más anchas, altas o largas, moverlas, y cambiar color y textura, con **simetría** (izquierda y derecha a la vez). |
| **Piezas** | Árbol con todos los cubos (hasta 128). Añadir, duplicar, espejar en X, borrar, centrar; nombre y «unida a» (jerarquía). |
| **Forma** | Posición, tamaño, pivote y giro en X/Y/Z (16 píxeles = 1 bloque). |
| **Pintar** | **Importar/exportar skins PNG**, pintura píxel a píxel (pincel, borrador, gotero, grosor y color), color base con selector HSV y más de 60 texturas (propias y de bloques de Minecraft). |
| **Animar** | 15 movimientos: cabeza, piernas, brazos, patas de araña, alas, cola, rebote, mandíbula, giro y balanceo. |
| **Atributos** | Vida, daño, velocidad, armadura, empuje, visión, escala y experiencia. |
| **Conducta** | Pasiva, neutral u hostil; camina o vuela; cuerpo a cuerpo, flechas, bolas de nieve, fuego o escupitajo; efecto al golpear; 27 sonidos; fuego, sol e ítem que la atrae. |
| **Aparición** | Aparición natural (horario, biomas, probabilidad), botín al morir y colores del huevo. |

**Modo fácil** (botón «Modo fácil» arriba): deja solo las pestañas **Cuerpo**, **Plantillas** y **Pintar**, para
montar una criatura pieza a pieza sin tocar números. Pulsa otra vez para volver a ver todas las pestañas.

**Botón «Ratón: Sí/No»** (en la barra de la vista 3D, tecla **M**): con él activado editas directamente sobre el modelo.
- **Mover** (G): arrastra la pieza, o una de sus flechas X/Y/Z para moverla en un solo eje.
- **Tamaño** (T): tira de los cuadraditos de las caras para agrandar o achicar la pieza.
- **Pincel** (B), **Borrador** (E) y **Gotero** (I): pinta píxel a píxel sobre cualquier pieza, con grosor
  de 1 a 8 (teclas - y +). La pintura se ve encima del color y la textura de la pieza.
- Mantén **Shift** para moverte de 0,25 en 0,25. Clic en el fondo: girar la vista. Clic derecho: desplazarla.

**Además**
- Deshacer/rehacer (Ctrl+Z / Ctrl+Y), guardar (Ctrl+S), duplicar (Ctrl+D), mover con las flechas.
- Huevos de criatura con el nombre y los colores de cada criatura.
- Exportar/importar criaturas como `.json` para compartirlas con amigos (incluyen la pintura).
- **Skins**: pon en tu criatura cualquier skin PNG con el formato de Minecraft (ver abajo).
- Funciona en un jugador y en servidores (el mod debe estar instalado en el servidor y en los clientes).

## Cómo compilarlo con GitHub (sin instalar nada)

1. Crea un repositorio nuevo en GitHub (puede ser privado).
2. Descomprime el `.zip` y entra en la carpeta `CreatureStudio`.
3. En la página del repositorio pulsa **Add file → Upload files** y arrastra **todo el contenido** de la
   carpeta (incluidas las carpetas `.github` y `gradle`, y los archivos `.gitignore` y `.gitattributes`).
   Pulsa **Commit changes**.
   - En Mac, Finder oculta las carpetas que empiezan por punto: pulsa `Cmd + Shift + .` para verlas.
   - Si la carpeta `.github` no se subiera, crea el workflow a mano: **Add file → Create new file**,
     escribe como nombre `.github/workflows/build.yml` y pega el contenido del archivo `build.yml`.
4. Ve a la pestaña **Actions**. La compilación empieza sola (o pulsa **Run workflow**). Tarda unos 5–10 minutos
   la primera vez porque descarga y prepara Minecraft.
5. Cuando aparezca el círculo verde ✔, entra en la ejecución y descarga el artefacto
   **CreatureStudio-NeoForge-1.21.1**. Dentro está el archivo `creaturestudio-neoforge-1.21.1-1.1.0.jar`.

Para compilar en tu PC: instala Java 21 y ejecuta `gradlew build` (Windows) o `./gradlew build` (Linux/Mac).
El `.jar` queda en `build/libs/`.

## Instalación

1. Instala **NeoForge 21.1.x** para **Minecraft 1.21.1** (https://neoforged.net).
2. Copia el `.jar` en la carpeta `mods` de tu `.minecraft`.
3. En un servidor, ponlo también en su carpeta `mods`.

## Cómo se usa

- **Abrir el estudio**: pulsa la tecla **K** (configurable en Controles) o haz clic derecho en el bloque
  **Estudio de Criaturas**. Receta (mesa de crafteo):

  ```
  Arcilla  Huevo   Arcilla
  Tablón   Libro   Tablón
  Tablón   Tablón  Tablón
  ```

- En la biblioteca: a la izquierda **Mis criaturas** con **+ Nueva criatura**, **Importar** y **Abrir carpeta**;
  a la derecha la criatura elegida con **Editar / Duplicar / Eliminar** y **Obtener huevo / Invocar / Exportar**.
- **+ Nueva criatura** abre el editor en la pestaña **Plantilla**: elige una plantilla o un modelo de Minecraft.
- Elige la pieza en **Piezas**, haciendo clic sobre ella en la vista 3D o con el selector «Pieza» que hay arriba
  de **Forma**, **Pintar** y **Animar**.
- Para pintar: pestaña **Pintar** → **Usar el ratón** (o tecla M y luego B) y pinta sobre el modelo.
- **Montar un cuerpo rápido**: pestaña **Cuerpo** (o activa el **Modo fácil**) → pulsa *Cuerpo 2 patas*,
  *Cabeza*, *Brazos* y *Piernas*. Haz clic en una parte para elegirla y usa *Más grande*, *+ Alto*, *Separar*...
- Coordenadas en píxeles: 16 = 1 bloque. El suelo está en Y = 0 y el frente de la criatura mira hacia **-Z**
  (la flecha azul de la rejilla). «Centrar modelo» la centra y la apoya en el suelo.
- **Guardar** la sube al mundo. Después, en la biblioteca:
  - **Obtener huevo**: en creativo es gratis; en supervivencia cuesta 1 huevo de gallina.
  - **Invocar**: la hace aparecer delante de ti (solo creativo u operadores).
  - **Exportar / Importar**: archivos en `.minecraft/creaturestudio/`.
- Las criaturas de cada mundo se guardan en `<carpeta del mundo>/creaturestudio/*.json`.
- En multijugador, solo el creador de una criatura (o un operador) puede editarla o borrarla.
- Si borras una criatura, todas sus copias desaparecen del mundo.

## Skins (texturas PNG)

Puedes poner en tu criatura una skin descargada de internet, por ejemplo la de un **aldeano**:

1. En **Plantillas → Modelos de Minecraft** busca el mob (p. ej. «aldeano») y haz clic para cargar su modelo.
   Así cada pieza sabe qué zona de la skin le toca.
2. Copia el archivo `.png` en la carpeta `.minecraft/creaturestudio/` (botón **Abrir carpeta** de la pestaña
   **Pintar**). También vale la subcarpeta `skins/`.
3. Pestaña **Pintar** → **Importar skin...** → pasa el ratón por los archivos para verlos y haz clic en el
   tuyo. Ctrl+Z lo deshace. Las skins HD (128×128...) se escalan solas.

**Exportar skin** guarda la textura completa de la criatura como `<nombre>_skin.png` en esa misma carpeta: ábrela
en cualquier programa de dibujo, píntala y vuelve a importarla. Funciona también con criaturas hechas desde cero
(cada pieza recibe su propia zona) y con las piezas que añadas a un modelo de Minecraft.

## Estructura del proyecto

```
src/main/java/com/creaturestudio/
  CreatureStudio.java      Clase principal del mod
  data/                    Definición de criaturas, JSON, plantillas, jerarquía
  entity/                  La entidad que adopta cualquier forma y su IA
  item/ block/ registry/   Huevo, bloque y registros
  network/                 Paquetes cliente <-> servidor
  server/                  Guardado en el mundo, permisos y aparición natural
  client/                  Renderizado 3D, editor, biblioteca e importación
src/main/resources/        Texturas, modelos, idiomas (es/en), receta y botín
.github/workflows/build.yml  Compilación automática en GitHub
```

## Problemas frecuentes

- **El workflow falla en «Compilar el mod»**: abre el paso para ver el error. Si es un problema de red
  al descargar dependencias, vuelve a ejecutarlo con **Re-run jobs**.
- **No veo la criatura**: comprueba que el mod está instalado también en el servidor.
- **Una textura sale morada y negra**: el identificador no existe; elige una de la lista o revisa el nombre
  (por ejemplo `minecraft:block/stone`).
- **Un modelo de Minecraft sale gris**: el mod no encontró su textura (busca por el nombre del mob, una lista de
  nombres especiales y la carpeta del mob); la forma se carga igual y puedes pintarla o importar su skin.
  Las capas transparentes (chaqueta, sombrero...) ya no salen como cajas grises: quedan invisibles.
- **La skin no encaja**: la imagen tiene que tener el formato del modelo que cargaste (por ejemplo, una skin de
  aldeano en el modelo de aldeano). Si tu criatura es propia, usa **Exportar skin** para obtener su plantilla.

Licencia MIT.
