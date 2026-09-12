<?php

use App\Mcp\Servers\SuperStackServer;
use Laravel\Mcp\Facades\Mcp;

Mcp::web('/mcp/superstack', SuperStackServer::class);
Mcp::local('superstack', SuperStackServer::class);
