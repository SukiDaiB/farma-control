# Farma Control 💊

Aplicativo Android pessoal para controle de remédios: lembretes no horário certo, controle de estoque, receitas e histórico de adesão. Funciona 100% offline, sem servidor e sem login.

## Funcionalidades

- **Lembretes insistentes** estilo despertador — tela cheia, som de alarme, toca por cima da tela de bloqueio e repete até você responder
- **Ações direto na notificação**: Tomei / Adiar / Pular
- **Controle de estoque** com aviso de que está acabando e botão "Comprei mais"
- **Receitas** com foto/anexo e aviso de validade
- **Histórico de adesão** com gráfico

### Em desenvolvimento

- Bloqueio por biometria
- Widget na tela inicial
- Backup automático (Android Auto Backup)

## Stack

- Kotlin + Jetpack Compose
- Room (banco local)
- AlarmManager com alarmes exatos + foreground service
- minSdk 26 · targetSdk 36

## Como instalar

Com o celular conectado via USB (depuração USB ativada):

```bash
./gradlew installDebug
```

> Em aparelhos Xiaomi/HyperOS, confirme a instalação na tela do celular e libere **início automático** e **sem restrições de bateria** para o app, senão os alarmes podem não tocar.

## Testes

```bash
./gradlew test
```
