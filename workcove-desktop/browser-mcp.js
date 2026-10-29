const CONTROL_URL = process.env.WORKCOVE_BROWSER_CONTROL_URL
const CONTROL_TOKEN = process.env.WORKCOVE_BROWSER_CONTROL_TOKEN

const SERVER_INSTRUCTIONS = [
  'Use WorkCove Browser when a task needs the user\'s logged-in web session, visual page interaction, or a browser download.',
  'This controls the visible WorkCove desktop browser and reuses its persistent login session.',
  'Use capability_set_update for the app_audit capability-set workflow. It resolves the site from the WorkCove website library and the account from the unlocked vault, then can save the matching version, download its latest package, and replace CAPA_CONFIG_AISG.json.',
  'capability_set_update defaults to preview mode. Set confirm=true only after the user explicitly asks to perform the update.',
  'Call browser_open first when the browser is closed.',
  'When a login form is visible, call browser_fill_credentials so the user can unlock and select a WorkCove vault entry. Never ask the user to paste a password into chat and never request or type a password through generic browser tools.',
  'If browser_open returns needsUserAction=true, stop and ask the user to complete the visible certificate or login prompt before calling browser_snapshot again.',
  'After navigation or form submission, call browser_snapshot before choosing a ref. Prefer ref-based click and type operations.',
  'Use browser_screenshot to verify visual results and browser_downloads to locate downloaded files.',
].join(' ')

const TOOLS = [
  {
    name: 'browser_open',
    description:
      'Open a URL in the visible WorkCove built-in browser. Reuses the existing logged-in browser session.',
    inputSchema: {
      type: 'object',
      additionalProperties: false,
      properties: {
        url: {
          type: 'string',
          description: 'Absolute http or https URL.',
        },
      },
      required: ['url'],
    },
  },
  {
    name: 'browser_status',
    description:
      'Return whether the WorkCove browser is open, its current URL, title, loading state, and error.',
    inputSchema: {
      type: 'object',
      additionalProperties: false,
      properties: {},
    },
  },
  {
    name: 'browser_snapshot',
    description:
      'Read the current page title, URL, visible text, and interactive elements. Elements include stable refs for click and type.',
    inputSchema: {
      type: 'object',
      additionalProperties: false,
      properties: {},
    },
  },
  {
    name: 'browser_click',
    description:
      'Click an element by snapshot ref or CSS selector and wait for navigation or UI updates.',
    inputSchema: {
      type: 'object',
      additionalProperties: false,
      properties: {
        ref: {
          type: 'string',
          description: 'Ref returned by browser_snapshot.',
        },
        selector: {
          type: 'string',
          description: 'CSS selector used when ref is not provided.',
        },
      },
    },
  },
  {
    name: 'browser_type',
    description:
      'Focus an input by snapshot ref or CSS selector, replace its value with text, and optionally press Enter.',
    inputSchema: {
      type: 'object',
      additionalProperties: false,
      properties: {
        ref: {
          type: 'string',
          description: 'Ref returned by browser_snapshot.',
        },
        selector: {
          type: 'string',
          description: 'CSS selector used when ref is not provided.',
        },
        text: {
          type: 'string',
          description: 'Text to insert.',
        },
        submit: {
          type: 'boolean',
          description: 'Press Enter after inserting text.',
        },
      },
      required: ['text'],
    },
  },
  {
    name: 'browser_press',
    description:
      'Send a keyboard key to the current page, optionally with modifier keys.',
    inputSchema: {
      type: 'object',
      additionalProperties: false,
      properties: {
        key: {
          type: 'string',
          description:
            'Electron key code such as Enter, Tab, Escape, ArrowDown, or a single character.',
        },
        modifiers: {
          type: 'array',
          items: {
            type: 'string',
            enum: ['shift', 'control', 'alt', 'meta'],
          },
        },
      },
      required: ['key'],
    },
  },
  {
    name: 'browser_fill_credentials',
    description:
      'Open the WorkCove vault picker for the current login page. If the vault is locked, the user is asked to unlock it in the app. The selected username and password are filled directly in the browser and never returned to the model.',
    inputSchema: {
      type: 'object',
      additionalProperties: false,
      properties: {
        credentialName: {
          type: 'string',
          description:
            'Optional exact vault entry name. If omitted, the user selects an entry and website-matching entries are shown first.',
        },
      },
    },
  },
  {
    name: 'browser_wait',
    description:
      'Wait for a duration or until a CSS selector exists in the page.',
    inputSchema: {
      type: 'object',
      additionalProperties: false,
      properties: {
        milliseconds: {
          type: 'number',
          description: 'Delay before returning, at most 30000.',
        },
        selector: {
          type: 'string',
          description: 'Wait until this CSS selector exists.',
        },
        timeoutMs: {
          type: 'number',
          description: 'Maximum selector wait time, at most 30000.',
        },
      },
    },
  },
  {
    name: 'browser_evaluate',
    description:
      'Run JavaScript in the current page and return a JSON-serializable result. Use for inspection when snapshot and standard interactions are insufficient.',
    inputSchema: {
      type: 'object',
      additionalProperties: false,
      properties: {
        script: {
          type: 'string',
          description:
            'JavaScript source evaluated in the page main world.',
        },
      },
      required: ['script'],
    },
  },
  {
    name: 'browser_screenshot',
    description:
      'Capture the visible browser page as an image. Optionally save a PNG to an absolute path.',
    inputSchema: {
      type: 'object',
      additionalProperties: false,
      properties: {
        path: {
          type: 'string',
          description: 'Optional absolute PNG path.',
        },
      },
    },
  },
  {
    name: 'browser_downloads',
    description:
      'List recent downloads from the WorkCove browser, including state, URL, and saved path.',
    inputSchema: {
      type: 'object',
      additionalProperties: false,
      properties: {},
    },
  },
  {
    name: 'capability_set_update',
    description:
      'Update the app_audit capability set from the WorkCove capability website. Resolves the site from the website library and credentials from the unlocked vault. Preview is the default; confirm=true saves the matching version, downloads the new package, and atomically replaces CAPA_CONFIG_AISG.json.',
    inputSchema: {
      type: 'object',
      additionalProperties: false,
      properties: {
        confirm: {
          type: 'boolean',
          description:
            'False or omitted only previews the matching capability. True performs the remote save and local replacement.',
        },
        websiteName: {
          type: 'string',
          description:
            'Optional exact name of the capability website in the WorkCove website library.',
        },
        credentialName: {
          type: 'string',
          description:
            'Optional exact vault entry name. By default the vault entry URL is matched to the website.',
        },
        repoPath: {
          type: 'string',
          description:
            'Optional absolute app_audit repository path. Defaults to /Users/panyong/shanjie/app_audit.',
        },
        productLine: {
          type: 'string',
          description:
            'Optional capability-site product line. By default it is read from the /productCapabilityList/<productLine> website URL.',
        },
      },
    },
  },
]

function writeMessage(message) {
  process.stdout.write(`${JSON.stringify(message)}\n`)
}

function success(id, result) {
  writeMessage({ jsonrpc: '2.0', id, result })
}

function failure(id, code, message) {
  writeMessage({
    jsonrpc: '2.0',
    id,
    error: { code, message },
  })
}

async function controlRequest(
  method,
  pathname,
  body,
  timeoutMs = 60_000,
) {
  if (!CONTROL_URL || !CONTROL_TOKEN) {
    throw new Error('WorkCove browser control is not configured')
  }
  const response = await fetch(`${CONTROL_URL}${pathname}`, {
    method,
    headers: {
      Authorization: `Bearer ${CONTROL_TOKEN}`,
      'Content-Type': 'application/json',
    },
    body: body === undefined ? undefined : JSON.stringify(body),
    signal: AbortSignal.timeout(timeoutMs),
  })
  const payload = await response.json().catch(() => ({}))
  if (!response.ok) {
    throw new Error(payload.error || `Browser control HTTP ${response.status}`)
  }
  return payload
}

function actionPath(name) {
  if (name === 'browser_open') return ['POST', '/v1/open']
  if (name === 'browser_status') return ['GET', '/v1/status']
  if (name === 'browser_snapshot') return ['GET', '/v1/snapshot']
  if (name === 'browser_click') return ['POST', '/v1/click']
  if (name === 'browser_type') return ['POST', '/v1/type']
  if (name === 'browser_press') return ['POST', '/v1/press']
  if (name === 'browser_fill_credentials') {
    return ['POST', '/v1/fill-credentials']
  }
  if (name === 'browser_wait') return ['POST', '/v1/wait']
  if (name === 'browser_evaluate') return ['POST', '/v1/evaluate']
  if (name === 'browser_screenshot') return ['POST', '/v1/screenshot']
  if (name === 'browser_downloads') return ['GET', '/v1/downloads']
  if (name === 'capability_set_update') {
    return ['POST', '/v1/capability-set/update']
  }
  return null
}

function toolTextResult(payload) {
  return {
    content: [
      {
        type: 'text',
        text: JSON.stringify(payload, null, 2),
      },
    ],
  }
}

async function callTool(name, args) {
  const route = actionPath(name)
  if (!route) throw new Error(`Unknown tool: ${name}`)
  const payload = await controlRequest(
    route[0],
    route[1],
    args,
    name === 'capability_set_update'
      ? 120_000
      : name === 'browser_fill_credentials'
        ? 130_000
        : 60_000,
  )
  if (
    name === 'browser_screenshot' &&
    typeof payload.data === 'string'
  ) {
    const { data, ...metadata } = payload
    return {
      content: [
        {
          type: 'text',
          text: JSON.stringify(metadata, null, 2),
        },
        {
          type: 'image',
          data,
          mimeType: payload.mimeType || 'image/png',
        },
      ],
    }
  }
  return toolTextResult(payload)
}

async function handleRequest(message) {
  const { id, method, params } = message
  if (method === 'initialize') {
    success(id, {
      protocolVersion:
        typeof params?.protocolVersion === 'string'
          ? params.protocolVersion
          : '2025-06-18',
      capabilities: {
        tools: { listChanged: false },
      },
      serverInfo: {
        name: 'workcove-browser',
        version: '0.2.8',
      },
      instructions: SERVER_INSTRUCTIONS,
    })
    return
  }
  if (method === 'ping') {
    success(id, {})
    return
  }
  if (method === 'tools/list') {
    success(id, { tools: TOOLS })
    return
  }
  if (method === 'tools/call') {
    try {
      const result = await callTool(
        params?.name,
        params?.arguments ?? {},
      )
      success(id, result)
    } catch (error) {
      success(id, {
        content: [
          {
            type: 'text',
            text:
              error instanceof Error
                ? error.message
                : 'Browser operation failed',
          },
        ],
        isError: true,
      })
    }
    return
  }
  if (id !== undefined && id !== null) {
    failure(id, -32601, `Unsupported method: ${method}`)
  }
}

function startStdioServer() {
  let buffer = ''
  process.stdin.setEncoding('utf8')
  process.stdin.on('data', (chunk) => {
    buffer += chunk
    let newline = buffer.indexOf('\n')
    while (newline >= 0) {
      const line = buffer.slice(0, newline).trim()
      buffer = buffer.slice(newline + 1)
      newline = buffer.indexOf('\n')
      if (!line) continue
      try {
        const message = JSON.parse(line)
        void handleRequest(message).catch((error) => {
          if (message?.id !== undefined && message?.id !== null) {
            failure(
              message.id,
              -32603,
              error instanceof Error
                ? error.message
                : 'Internal browser MCP error',
            )
          }
        })
      } catch (error) {
        process.stderr.write(
          `[workcove-browser] ${
            error instanceof Error ? error.message : String(error)
          }\n`,
        )
      }
    }
  })
}

if (require.main === module) {
  startStdioServer()
}

module.exports = {
  SERVER_INSTRUCTIONS,
  TOOLS,
  callTool,
  handleRequest,
}
