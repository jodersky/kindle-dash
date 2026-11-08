package server

/** URLs to render, set from the command line in `run` before the server starts. */
object Config:
  /** Grafana image-renderer URL, served as /dashboard.png */
  @volatile var renderUrl: String = null
  /** Grafana dashboard page, screenshotted with Playwright, served as /dashboard2.png */
  @volatile var dashboardUrl: String = null

@keg.get("/dashboard.png")
def get(req: keg.Request) =
  try
    val png = requests.get(Config.renderUrl).bytes
    keg.Response(
      toGrayscalePng(png),
      200,
      headers = Seq("content-type" -> "image/png")
    )
  catch
    case e: Exception =>
      e.printStackTrace()
      keg.abort(500, "error")

/** Decode a PNG and re-encode it as an 8-bit grayscale PNG. */
def toGrayscalePng(png: Array[Byte]): Array[Byte] =
  import java.awt.Color
  import java.awt.image.BufferedImage
  import javax.imageio.ImageIO

  val src = ImageIO.read(java.io.ByteArrayInputStream(png))
  if src == null then throw IllegalArgumentException("response is not a decodable image")

  val gray = BufferedImage(src.getWidth, src.getHeight, BufferedImage.TYPE_BYTE_GRAY)
  val g = gray.createGraphics()
  try
    g.setColor(Color.WHITE) // flatten any transparency onto white
    g.fillRect(0, 0, src.getWidth, src.getHeight)
    g.drawImage(src, 0, 0, null)
  finally g.dispose()

  val out = java.io.ByteArrayOutputStream()
  ImageIO.write(gray, "png", out)
  val bytes = out.toByteArray

  bytes

/** Renders a Grafana dashboard with a headless Chromium, instead of using
  * Grafana's own /render endpoint.
  *
  * Playwright objects are not thread-safe, so a single browser is shared and
  * all access goes through `synchronized`.
  */
object BrowserRenderer:
  import com.microsoft.playwright.*
  import com.microsoft.playwright.options.{LoadState, WaitUntilState}

  private var playwright: Playwright = null
  private var browser: Browser = null

  private def ensureBrowser(): Browser =
    if browser == null || !browser.isConnected then
      if playwright == null then playwright = Playwright.create()
      browser = playwright.chromium().launch()
    browser

  def screenshot(
      url: String,
      width: Int,
      height: Int,
      scale: Double,
      css: String = null
  ): Array[Byte] = synchronized:
    val ctx = ensureBrowser().newContext(
      Browser.NewContextOptions()
        .setViewportSize(width, height)
        .setDeviceScaleFactor(scale)
    )
    try
      val page = ctx.newPage()
      page.navigate(
        url,
        Page.NavigateOptions()
          .setWaitUntil(WaitUntilState.NETWORKIDLE)
          .setTimeout(30000)
      )
      // Grafana is a SPA: "network idle" is reached once the app shell has
      // loaded, before any panel exists. Wait for panels to be mounted, for
      // their queries to finish, and for loading indicators to disappear.
      page.waitForSelector("[data-viz-panel-key], .react-grid-item")
      page.waitForLoadState(LoadState.NETWORKIDLE)
      page.waitForFunction(
        "() => document.querySelectorAll('[aria-label=\"Panel loading bar\"], [data-testid*=\"loading\" i]').length === 0"
      )
      if css != null then
        page.addStyleTag(Page.AddStyleTagOptions().setContent(css))
      page.screenshot()
    finally ctx.close()

  def close(): Unit = synchronized:
    if playwright != null then playwright.close() // also closes the browser
    playwright = null
    browser = null

@keg.get("/dashboard2.png")
def getPlaywright(req: keg.Request) =
  try
    // 583x780 CSS pixels at 1.3x ≈ 758x1014 device pixels, the same output
    // size as /dashboard.png, but with everything drawn 30% larger.
    val png = BrowserRenderer.screenshot(
      Config.dashboardUrl,
      width = 758,
      height = 1014,
      scale = 1,
      css = "[data-testid*='Panel header'] h2 { font-size: 24px !important; }"
    )
    keg.Response(
      toGrayscalePng(png),
      200,
      headers = Seq("content-type" -> "image/png")
    )
  catch
    case e: Exception =>
      e.printStackTrace()
      keg.abort(500, "error")

val routes = keg.routes()

def main(args: Array[String]): Unit = clip.main(this, args)

@clip.command()
def run(
  @clip.arg("--render-url", help = "Grafana render URL (/render/d/...), served as /dashboard.png")
  renderUrl: String,
  @clip.arg("--dashboard-url", help = "Grafana dashboard URL (/d/...), rendered with a headless browser and served as /dashboard2.png")
  dashboardUrl: String,
  port: Int = 8081
) =
  Config.renderUrl = renderUrl
  Config.dashboardUrl = dashboardUrl
  val server = keg.Server(
    routes,
    listeners = Seq(
      keg.Listener.Http(port = port)
    )
  )

  sys.addShutdownHook:
    server.stop()
    server.awaitTermination()
    BrowserRenderer.close()

  server.start()
  server.awaitTermination()
