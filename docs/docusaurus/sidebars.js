// @ts-check

/** @type {import('@docusaurus/plugin-content-docs').SidebarsConfig} */
const sidebars = {
  // By default, Docusaurus generates a sidebar from the docs folder structure
  // tutorialSidebar: [{type: 'autogenerated', dirName: '.'}],

  // But you can create a sidebar manually

  tutorialsSidebar: [
    'index',
    'connections/connection',
    {
      type: 'category',
      label: 'Credentials',
      link: {
        type: 'generated-index',
        title: 'Credentials',
        description: 'Credentials tutorials'
      },
      items: [
        'credentials/issue',
        'credentials/present-proof'
      ]
    },
    {
      type: 'category',
      label: 'DIDs',
      link: {
        type: 'generated-index',
        title: 'DIDs',
        description: 'DIDs tutorials'
      },
      items: [
        'dids/create',
        'dids/update',
        'dids/publish',
        'dids/deactivate'
      ]
    },
    {
      type: 'category',
      label: 'Schemas',
      link: {
        type: 'generated-index',
        title: 'Schemas',
        description: 'Schema tutorials'
      },
      items: [
        'schemas/credential-schema',
        'schemas/create',
        'schemas/update',
        'schemas/delete'
      ]
    },
    {
      type: 'category',
      label: 'Credential Definition',
      link: {
        type: 'generated-index',
        title: 'Credential Definition',
        description: 'Credential Definition Tutorials'
      },
      items: [
        'credentialdefinition/credential-definition',
        'credentialdefinition/create',
        'credentialdefinition/delete'
      ]
    },
    {
      type: 'category',
      label: 'Secret Management',
      link: {
        type: 'generated-index',
        title: 'Secret Management',
        description: 'Secret Management'
      },
      items: [
        'secrets/operation',
        'secrets/seed-generation'
      ]
    },
    {
      type: 'category',
      label: 'Webhooks',
      items: [
        'webhooks/webhook'
      ]
    },
    {
      type: 'category',
      label: 'Multi-Tenancy',
      items: [
        'multitenancy/tenant-onboarding',
        'multitenancy/tenant-onboarding-ext-iam',
        'multitenancy/tenant-onboarding-self-service',
        'multitenancy/tenant-migration',
      ]
    }
  ]
}

module.exports = sidebars
