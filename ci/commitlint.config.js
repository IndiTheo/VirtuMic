module.exports = {
  extends: ['@commitlint/config-conventional'],
  parserPreset: {
    parserOpts: {
      headerPattern: /^([a-z-]+)(?:\((.*)\))?!?: (.*)$/,
      headerCorrespondence: ['type', 'scope', 'subject']
    }
  },
  rules: {
    'type-enum': [
      2,
      'always',
      [
        // Production / Standard types (triggers releases & user-facing release notes / housekeeping)
        'feat',
        'fix',
        'perf',
        'refactor',
        'style',
        'test',
        'build',
        'chore',
        'revert',
        'docs',
        'ci',
        // Non-Code Aspects: CI (`ci-*`) - excluded from user-facing release notes & SemVer bumps
        'ci-feat',
        'ci-fix',
        'ci-perf',
        'ci-refactor',
        'ci-style',
        'ci-test',
        'ci-build',
        'ci-chore',
        // Non-Code Aspects: Docs (`docs-*`) - streamlined to core documentation actions
        'docs-feat',
        'docs-fix',
        'docs-perf'
      ]
    ]
  }
};
