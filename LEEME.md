# Cuídame

Avisa a alguien de confianza si una persona se cae y no responde, o si
pasa demasiadas horas sin moverse.

## Qué hace, exactamente

**Detecta caídas.** El acelerómetro busca la secuencia completa de una
caída: el instante de caída libre, el golpe, y —lo más importante— si la
persona se queda quieta después. Quien se cae y está bien se levanta y se
mueve; quien se ha hecho daño, no.

**Detecta falta de actividad.** Esto es lo que más gente encuentra a
tiempo, y casi nadie lo tiene. Un detector de caídas no sirve si a la
persona le da un ictus sentada, o se encuentra mal y se acuesta, o se
desmaya con el móvil en la mesa. Pero un móvil que lleva cuatro horas sin
registrar ni un movimiento un martes a las once de la mañana sí dice algo.
De noche no vigila, porque lo raro sería moverse.

**Avisa por SMS.** Con la hora y un enlace al sitio donde está. Por SMS y
no por WhatsApp porque WhatsApp no deja que una app envíe sola: siempre
tiene que haber alguien que pulse "enviar", y una persona inconsciente no
puede. El SMS sale solo y no necesita internet, solo cobertura.

## Lo que NO hace, a propósito

- No manda nada a ningún servidor. Todo se calcula dentro del móvil. No
  hay cuenta, ni nube, ni nada que se pueda caer o quedarse sin cuota.
- No graba audio ni vídeo. Solo lee el acelerómetro.
- No comparte la ubicación con nadie salvo en el mensaje de emergencia.
- No diagnostica nada. Es una ayuda, no un aparato médico.

## La pieza más importante: el minuto de confirmación

Entre "creo que se ha caído" y "aviso a su hija" hay siempre un minuto en
el que la persona puede decir que está bien, con un botón que ocupa media
pantalla, sonido, vibración y voz.

Esto no es un adorno. Ningún detector acierta siempre: el móvil se cae de
la mesa, uno se sienta de golpe. Si cada uno de esos casos mandara un
aviso, la familia silenciaría los mensajes en tres días, y el día que
pasara de verdad no lo vería nadie. **Un detector que se equivoca mucho es
peor que no tener ninguno, porque da confianza falsa.**

## Sus límites, dichos claramente

El móvil tiene que ir **encima** de la persona. En el bolsillo detecta
razonablemente; en un bolso colgado, peor; encima de la mesa, nada. Un
reloj en la muñeca lo hace mejor que cualquier móvil.

Es una ayuda más. No sustituye a un aviso médico ni al 112.

## Probarlo sin caerse

La pantalla principal tiene dos botones para eso:

- **Ver la alarma**: enseña la pantalla de emergencia entera, con su
  cuenta atrás, sin avisar a nadie.
- **Mandar un mensaje de prueba**: manda un SMS de verdad al contacto,
  diciendo que es una prueba.

Nadie debería fiar su seguridad a una app que no ha visto funcionar antes.

## Instalar

El APK se compila solo en GitHub y se publica siempre en el mismo enlace:

    https://github.com/ferguet/cuidame-android/releases/tag/ultima

Se guarda en favoritos del móvil, se toca el `.apk` y se instala. Android
avisará de que viene de fuera de la tienda: es normal en una app propia.
