import fetch from 'isomorphic-fetch'

export const IMPORT = '__IMPORT__'

export default {
  [IMPORT] ({commit}, file) {
    const formData = new FormData()
    formData.append('file', file)

    const options = {
      body: formData,
      method: 'POST',
      credentials: 'same-origin'
    }

    fetch('/plugin/one-click-importer/upload', options).then(response => {
      console.log(response)
      const dataSetUri = response.headers.get('Location')
      const entityId = dataSetUri.substring(dataSetUri.lastIndexOf('/') + 1)
      window.location.href = 'http://localhost:8080/menu/main/dataexplorer?entity=' + entityId
    }, error => {
      console.log(error)
    })
  }
}
