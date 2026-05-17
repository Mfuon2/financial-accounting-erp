package com.qesuite.accounting.shared.docs

import org.springframework.stereotype.Controller
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.ResponseBody

@Controller
class ScalarController {

    @GetMapping("/docs")
    @ResponseBody
    fun getDocs(): String {
        return """
            <!DOCTYPE html>
            <html>
            <head>
              <meta charset="utf-8">
              <meta name="viewport" content="width=device-width, initial-scale=1">
              <title>API Reference | QESuite Accounting</title>
              <!-- Scalar UI from CDN -->
              <script src="https://cdn.jsdelivr.net/npm/@scalar/api-reference@latest/dist/browser/standalone.js"></script>
              <style>
                html, body { margin: 0; padding: 0; height: 100%; }
              </style>
            </head>
            <body>
              <div id="scalar-container"></div>
              <script>
                // Point to your SpringDoc OpenAPI JSON endpoint
                const specUrl = window.location.origin + '/v3/api-docs.json';
                
                Scalar.createApiReference(document.getElementById('scalar-container'), {
                  spec: {
                    url: specUrl
                  },
                  theme: 'default',
                  layout: 'modern',
                  authentication: {
                    http: { basic: { username: '', password: '' } },
                    apiKey: { token: '' }
                  }
                });
              </script>
            </body>
            </html>
        """.trimIndent()
    }
}
